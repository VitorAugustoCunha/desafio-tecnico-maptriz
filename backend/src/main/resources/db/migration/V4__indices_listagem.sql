-- Indices que sustentam a listagem paginada e filtrada.
--
-- Os efeitos medidos de cada um estao em docs/PERFORMANCE.md, com
-- EXPLAIN (ANALYZE, BUFFERS) antes e depois.

-- Busca parcial case-insensitive (ILIKE '%texto%') nao usa indice B-tree: o
-- curinga a esquerda impede. pg_trgm resolve com indice GIN de trigramas, que e
-- a estrategia indexavel do PostgreSQL para esse caso.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Filtro por proprietario e o JOIN da listagem.
CREATE INDEX idx_imovel_proprietario_id ON imovel (proprietario_id);

-- Filtros textuais parciais.
--
-- Os indices sao sobre `lower(coluna)`, e nao sobre a coluna crua, porque a
-- busca case-insensitive da aplicacao gera `lower(coluna) LIKE lower(?)`. Um
-- indice sobre `municipio` nao seria usado por esse predicado.
CREATE INDEX idx_imovel_municipio_trgm  ON imovel       USING gin (lower(municipio) gin_trgm_ops);
CREATE INDEX idx_proprietario_nome_trgm ON proprietario USING gin (lower(nome)      gin_trgm_ops);

-- Ordenacoes permitidas pela whitelist da API. O `id` ao final desempata e
-- torna a ordem total — sem isso, a paginacao por offset pode repetir ou pular
-- linhas entre paginas quando ha valores iguais na coluna ordenada.
CREATE INDEX idx_imovel_municipio_id ON imovel (municipio, id);
CREATE INDEX idx_imovel_area_id      ON imovel (area_m2, id);
CREATE INDEX idx_imovel_criado_id    ON imovel (criado_em DESC, id DESC);

-- A listagem exibe por padrao apenas imoveis ativos; indice parcial cobre esse
-- recorte ocupando bem menos que um indice completo.
CREATE INDEX idx_imovel_ativo_id ON imovel (id) WHERE ativo;
