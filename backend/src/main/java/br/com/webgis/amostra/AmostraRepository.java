package br.com.webgis.amostra;

import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

/**
 * Insercao em massa dos lotes de amostra.
 *
 * <p>Tudo em <b>uma</b> instrucao: gerar mil lotes com mil {@code INSERT} (ou,
 * pior, mil chamadas HTTP) levaria minutos e tomaria o advisory lock de
 * geometria mil vezes. Aqui o {@code generate_series} monta a grade, o PostGIS
 * projeta e o banco grava de uma vez.
 *
 * <p>Como no resto do projeto, <b>nenhum</b> valor entra por concatenacao —
 * inclusive a lista de proprietarios, que viaja como texto e e convertida em
 * array pelo proprio Postgres.
 */
@Repository
public class AmostraRepository {

	private final EntityManager em;

	public AmostraRepository(EntityManager em) {
		this.em = em;
	}

	/**
	 * Insere uma grade de lotes retangulares que nao se tocam.
	 *
	 * <p>A geometria sai da mesma funcao {@code webgis_retangulo} usada pelo
	 * cadastro normal, e a partir da latitude/longitude <b>ja arredondadas</b>
	 * para as 7 casas da coluna. Sem isso, o poligono gravado seria o de um
	 * centro ligeiramente diferente do que a tela mostra, e a pre-visualizacao do
	 * formulario nao bateria com o que esta no banco.
	 *
	 * <p>O {@code NOT EXISTS} e rede de seguranca: as celulas da grade nao se
	 * cruzam entre si por construcao, mas o espaco pode ja estar ocupado por um
	 * imovel cadastrado antes. Lote conflitante e pulado, nao derruba a carga.
	 *
	 * @param idsProprietarios ids separados por virgula, distribuidos em rodizio
	 * @param linhaInicial     primeira linha da grade — desloca o bloco para cima
	 *                         dos lotes de amostra que ja existem
	 * @return quantos lotes entraram de fato
	 */
	public int inserirGrade(String idsProprietarios, int quantidade, int linhaInicial, int colunas,
			double passoX, double passoY, double latitudeBase, double longitudeBase,
			String municipio, String uf, String bairro) {

		return em.createNativeQuery("""
				WITH origem AS (
				    SELECT ST_Transform(
				               ST_SetSRID(ST_MakePoint(
				                   CAST(:longitudeBase AS double precision),
				                   CAST(:latitudeBase  AS double precision)), 4326),
				               31982) AS p
				),
				titulares AS (
				    SELECT t.id,
				           ROW_NUMBER() OVER (ORDER BY t.ordem) - 1 AS posicao,
				           COUNT(*)     OVER ()                     AS total
				      FROM unnest(CAST(string_to_array(CAST(:idsProprietarios AS text), ',') AS bigint[]))
				           WITH ORDINALITY AS t(id, ordem)
				),
				celulas AS (
				    SELECT i,
				           i %  CAST(:colunas AS integer)                                AS coluna,
				           CAST(:linhaInicial AS integer) + i / CAST(:colunas AS integer) AS linha,
				           12 + (i %  7) * 2                                             AS largura_m,
				           20 + (i % 11) * 2                                             AS comprimento_m
				      FROM generate_series(0, CAST(:quantidade AS integer) - 1) AS i
				),
				centros AS (
				    SELECT c.*,
				           ST_Transform(
				               ST_SetSRID(ST_MakePoint(
				                   ST_X(o.p) + c.coluna * CAST(:passoX AS double precision),
				                   ST_Y(o.p) + c.linha  * CAST(:passoY AS double precision)), 31982),
				               4326) AS ponto
				      FROM celulas c CROSS JOIN origem o
				),
				lotes AS (
				    SELECT c.i, c.coluna, c.linha, c.largura_m, c.comprimento_m,
				           ROUND(CAST(ST_Y(c.ponto) AS numeric), 7) AS latitude,
				           ROUND(CAST(ST_X(c.ponto) AS numeric), 7) AS longitude
				      FROM centros c
				),
				prontos AS (
				    SELECT l.*,
				           webgis_retangulo(
				               CAST(l.longitude     AS double precision),
				               CAST(l.latitude      AS double precision),
				               CAST(l.largura_m     AS double precision),
				               CAST(l.comprimento_m AS double precision)) AS geom
				      FROM lotes l
				)
				INSERT INTO imovel (proprietario_id, municipio, uf, bairro, rua, numero,
				                    latitude, longitude, area_m2, largura_m, comprimento_m, ativo, geom)
				SELECT t.id,
				       CAST(:municipio AS varchar),
				       CAST(:uf        AS varchar),
				       CAST(:bairro    AS varchar),
				       'Rua Amostra ' || (p.linha + 1),
				       CAST((p.coluna + 1) * 10 AS varchar),
				       p.latitude,
				       p.longitude,
				       CAST(p.largura_m * p.comprimento_m AS numeric(12,2)),
				       CAST(p.largura_m     AS numeric(10,2)),
				       CAST(p.comprimento_m AS numeric(10,2)),
				       TRUE,
				       p.geom
				  FROM prontos p
				  JOIN titulares t ON t.posicao = p.i % t.total
				 WHERE p.geom IS NOT NULL
				   AND ST_IsValid(p.geom)
				   AND NOT EXISTS (SELECT 1
				                     FROM imovel ex
				                    WHERE ex.geom IS NOT NULL
				                      AND ST_Intersects(ex.geom, p.geom))
				""")
				.setParameter("idsProprietarios", idsProprietarios)
				.setParameter("quantidade", quantidade)
				.setParameter("linhaInicial", linhaInicial)
				.setParameter("colunas", colunas)
				.setParameter("passoX", passoX)
				.setParameter("passoY", passoY)
				.setParameter("latitudeBase", latitudeBase)
				.setParameter("longitudeBase", longitudeBase)
				.setParameter("municipio", municipio)
				.setParameter("uf", uf)
				.setParameter("bairro", bairro)
				.executeUpdate();
	}

	/** Quantos lotes de amostra ja existem — define onde o proximo bloco comeca. */
	public long contar(String municipio, String bairro) {
		Object total = em.createNativeQuery("""
				SELECT count(*)
				  FROM imovel
				 WHERE municipio = CAST(:municipio AS varchar)
				   AND bairro    = CAST(:bairro    AS varchar)
				""")
				.setParameter("municipio", municipio)
				.setParameter("bairro", bairro)
				.getSingleResult();

		return ((Number) total).longValue();
	}

	/**
	 * Centro do conjunto de amostra, em 4326.
	 *
	 * <p>Serve para a tela saber para onde levar o mapa depois da carga — mil
	 * lotes gerados fora da area visivel pareceriam nao ter sido criados.
	 */
	public Centro centro(String municipio, String bairro) {
		@SuppressWarnings("unchecked")
		List<Object[]> linhas = em.createNativeQuery("""
				SELECT ROUND(AVG(latitude),  7), ROUND(AVG(longitude), 7)
				  FROM imovel
				 WHERE municipio = CAST(:municipio AS varchar)
				   AND bairro    = CAST(:bairro    AS varchar)
				""")
				.setParameter("municipio", municipio)
				.setParameter("bairro", bairro)
				.getResultList();

		Object[] linha = linhas.isEmpty() ? null : linhas.get(0);

		if (linha == null || linha[0] == null || linha[1] == null) {
			return null;
		}

		return new Centro(((Number) linha[0]).doubleValue(), ((Number) linha[1]).doubleValue());
	}

	public record Centro(double latitude, double longitude) {
	}
}
