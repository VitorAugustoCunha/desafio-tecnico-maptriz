-- Geometria real do imovel (tarefa 8) e suporte a consulta espacial por viewport.
--
-- CRS: a entrada chega em WGS 84 (EPSG:4326, graus) e a geometria e persistida
-- em EPSG:31982 (SIRGAS 2000 / UTM zona 22S, metros), como o enunciado exige.
-- Trabalhar em metros e o que permite montar o retangulo a partir de largura e
-- comprimento e medir distancia/area sem distorcao local.
--
-- LIMITACAO ASSUMIDA: 31982 e uma zona UTM especifica (22S, meridiano central
-- 51W). Ela cobre bem o Sul/Sudeste, mas imoveis fora da zona sofrem distorcao
-- crescente conforme se afastam. O enunciado fixa esse SRID, entao ele foi
-- mantido; a alternativa (SRID por zona, ou um CRS unico como 5880) esta
-- discutida em docs/DECISIONS.md, ADR-004. Nao troquei o SRID em silencio.

CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE imovel ADD COLUMN largura_m     numeric(10,2);
ALTER TABLE imovel ADD COLUMN comprimento_m numeric(10,2);
ALTER TABLE imovel ADD COLUMN geom          geometry(POLYGON, 31982);

COMMENT ON COLUMN imovel.geom IS
    'Retangulo do lote em EPSG:31982. NULL nos imoveis legados, que so tem ponto.';

-- Ponto em 4326 derivado de latitude/longitude. Coluna gerada: nao ha como
-- ficar dessincronizada do par lat/lon, e permite indice espacial para a
-- consulta por viewport do mapa (imoveis legados nao tem poligono).
ALTER TABLE imovel ADD COLUMN ponto geometry(POINT, 4326)
    GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)
    ) STORED;

-- Dimensoes andam em par: ou o imovel tem largura E comprimento (e entao tem
-- poligono), ou nao tem nenhuma das duas (imovel legado, so ponto).
ALTER TABLE imovel ADD CONSTRAINT ck_imovel_dimensoes_em_par
    CHECK ((largura_m IS NULL) = (comprimento_m IS NULL));

ALTER TABLE imovel ADD CONSTRAINT ck_imovel_dimensoes_positivas
    CHECK ((largura_m IS NULL OR largura_m > 0) AND (comprimento_m IS NULL OR comprimento_m > 0));

-- Geometria invalida nao entra, venha de onde vier.
ALTER TABLE imovel ADD CONSTRAINT ck_imovel_geom_valida
    CHECK (geom IS NULL OR ST_IsValid(geom));

-- Indices espaciais: o GiST de `geom` sustenta a checagem de sobreposicao
-- (ST_Intersects) e o de `ponto` sustenta o recorte por viewport do mapa.
CREATE INDEX idx_imovel_geom_gist  ON imovel USING gist (geom);
CREATE INDEX idx_imovel_ponto_gist ON imovel USING gist (ponto);

-- Construcao do retangulo do lote.
--
-- Convencao adotada e documentada: latitude/longitude sao o CENTRO do lote.
-- Metade da largura vai para cada lado no eixo X (leste-oeste) e metade do
-- comprimento para cada lado no eixo Y (norte-sul), ja em metros, no plano
-- projetado 31982.
--
-- A projecao 4326 -> 31982 fica a cargo do PostGIS/PROJ em vez de uma
-- biblioteca Java equivalente: e a mesma engine que valida a geometria, indexa
-- e consulta, entao nao ha risco de divergencia entre o que a aplicacao calcula
-- e o que o banco compara (ver docs/DECISIONS.md, ADR-004).
CREATE OR REPLACE FUNCTION webgis_retangulo(
    p_longitude     double precision,
    p_latitude      double precision,
    p_largura_m     double precision,
    p_comprimento_m double precision
) RETURNS geometry AS $$
    SELECT ST_MakeEnvelope(
        ST_X(c.p) - p_largura_m     / 2.0,
        ST_Y(c.p) - p_comprimento_m / 2.0,
        ST_X(c.p) + p_largura_m     / 2.0,
        ST_Y(c.p) + p_comprimento_m / 2.0,
        31982
    )
    FROM (
        SELECT ST_Transform(ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326), 31982) AS p
    ) c;
$$ LANGUAGE sql IMMUTABLE STRICT;

COMMENT ON FUNCTION webgis_retangulo IS
    'Retangulo do lote em EPSG:31982 a partir do centro (lon/lat WGS84) e das dimensoes em metros.';
