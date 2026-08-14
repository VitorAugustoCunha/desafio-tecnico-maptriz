-- Gerador de volume para medir a listagem sob carga (tarefa 6).
--
-- NAO faz parte das migrations: dado sintetico de teste nao pode entrar na
-- trilha versionada do schema. Rode a mao, contra um banco de avaliacao:
--
--   docker compose exec -T banco psql -U webgis -d webgis \
--     -v imoveis=500000 -v proprietarios=50000 -v municipios=1200 \
--     -f /dev/stdin < scripts/seed-volume.sql
--
-- Os numeros medidos com este seed estao em docs/PERFORMANCE.md.

\set ON_ERROR_STOP on

\if :{?imoveis}
\else
  \set imoveis 500000
\endif

\if :{?proprietarios}
\else
  \set proprietarios 50000
\endif

\if :{?municipios}
\else
  \set municipios 1200
\endif

\echo 'Gerando' :proprietarios 'proprietarios,' :imoveis 'imoveis em' :municipios 'municipios...'

-- Proprietarios sinteticos. O nome recebe o mesmo tratamento de normalizacao da
-- aplicacao, senao a constraint unica rejeitaria a carga.
INSERT INTO proprietario (nome, nome_normalizado)
SELECT nome, webgis_normaliza_nome(nome)
FROM (
    SELECT 'Titular ' || to_char(g, 'FM000000') AS nome
    FROM generate_series(1, :proprietarios) AS g
) AS gerados
ON CONFLICT (nome_normalizado) DO NOTHING;

-- Imoveis espalhados pelo territorio brasileiro, distribuidos entre os
-- proprietarios e os municipios gerados.
INSERT INTO imovel (
    proprietario_id, municipio, uf, bairro, rua, numero,
    latitude, longitude, area_m2, ativo, criado_em, atualizado_em
)
SELECT
    -- Distribuicao desigual de proposito: alguns titulares com muitos imoveis,
    -- que e como o cadastro real se comporta.
    (SELECT id FROM proprietario ORDER BY id LIMIT 1)
        + (g::bigint * 7919) % :proprietarios,
    'Municipio ' || to_char((g::bigint * 104729) % :municipios, 'FM0000'),
    (ARRAY['SP','RJ','MG','PR','RS','SC','BA','PE','CE','GO'])[1 + (g % 10)],
    'Bairro ' || (1 + (g % 40)),
    'Rua ' || (1 + (g % 900)),
    (1 + (g % 4000))::text,
    -- Brasil continental, com uma casa decimal de dispersao pseudo-aleatoria.
    (-33.0 + (g % 3800) * 0.01)::numeric(10,7),
    (-73.0 + (g % 3900) * 0.01)::numeric(10,7),
    (50 + (g % 4950))::numeric(12,2),
    (g % 25) <> 0,
    now() - ((g % 3650) || ' days')::interval,
    now() - ((g % 3650) || ' days')::interval
FROM generate_series(1, :imoveis) AS g;

-- O planejador precisa de estatisticas atualizadas; sem isto a primeira medicao
-- mede o otimizador desinformado, nao o indice.
ANALYZE imovel;
ANALYZE proprietario;

\echo 'Pronto. Totais:'
SELECT
    (SELECT count(*) FROM imovel)       AS imoveis,
    (SELECT count(*) FROM proprietario) AS proprietarios,
    (SELECT count(DISTINCT municipio) FROM imovel) AS municipios_distintos,
    pg_size_pretty(pg_total_relation_size('imovel')) AS tamanho_imovel;
