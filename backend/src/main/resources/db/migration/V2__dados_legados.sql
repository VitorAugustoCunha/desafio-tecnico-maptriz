-- Dados pre-existentes, com o proprietario ainda como TEXTO LIVRE.
--
-- Sao as mesmas 12 linhas do `data.sql` original. Estao na trilha versionada de
-- proposito: o enunciado diz que "a base ja tem imoveis cadastrados com o
-- proprietario em texto" e que "a migracao nao pode perde-los". Manter esses
-- dados aqui e o que torna a V3 reproduzivel e testavel — o teste de migracao
-- roda a V2, confere o estado, aplica a V3 e prova que nada se perdeu.
--
-- Diferente do `data.sql` + `spring.sql.init.mode=always`, isto NAO duplica a
-- cada subida: o Flyway aplica uma unica vez, e o INSERT ainda e guardado por
-- NOT EXISTS para o caso de a tabela ja ter sido populada por outro caminho.
--
-- Em um produto real, dado de demonstracao viveria fora da trilha de migration
-- (ver docs/DECISIONS.md, ADR-002).

INSERT INTO imovel
    (proprietario, municipio, uf, bairro, rua, numero, latitude, longitude, area_m2, ativo)
SELECT v.proprietario, v.municipio, v.uf, v.bairro, v.rua, v.numero,
       v.latitude, v.longitude, v.area_m2, v.ativo
FROM (VALUES
    ('Maria Aparecida Souza',   'São Paulo',      'SP', 'Pinheiros',        'Rua dos Pinheiros',        '1245', -23.5629000::numeric, -46.6944000::numeric, 320.50::numeric, TRUE),
    ('João Carlos Ferreira',    'São Paulo',      'SP', 'Santana',          'Avenida Braz Leme',        '890',  -23.5010000,          -46.6280000,          450.00,          TRUE),
    ('Ana Beatriz Lima',        'Rio de Janeiro', 'RJ', 'Copacabana',       'Rua Barata Ribeiro',       '512',  -22.9686000,          -43.1869000,          180.75,          TRUE),
    ('Carlos Eduardo Nunes',    'Rio de Janeiro', 'RJ', 'Tijuca',           'Rua Conde de Bonfim',      '1100', -22.9245000,          -43.2340000,          210.00,          TRUE),
    ('Fernanda Ribeiro Alves',  'Belo Horizonte', 'MG', 'Savassi',          'Rua Pernambuco',           '77',   -19.9370000,          -43.9350000,          265.30,          TRUE),
    ('Roberto Antunes Melo',    'Curitiba',       'PR', 'Batel',            'Avenida do Batel',         '1560', -25.4420000,          -49.2920000,          390.00,          TRUE),
    ('Patrícia Gomes Duarte',   'Porto Alegre',   'RS', 'Moinhos de Vento', 'Rua Padre Chagas',         '340',  -30.0250000,          -51.2050000,          155.60,          TRUE),
    ('Luiz Henrique Barbosa',   'Salvador',       'BA', 'Barra',            'Avenida Oceânica',         '2200', -13.0100000,          -38.5330000,          275.90,          TRUE),
    ('Juliana Martins Rocha',   'Recife',         'PE', 'Boa Viagem',       'Avenida Boa Viagem',       '4500',  -8.1200000,          -34.9000000,          198.40,          TRUE),
    ('Marcos Vinícius Teixeira','Fortaleza',      'CE', 'Aldeota',          'Avenida Santos Dumont',    '3131',  -3.7350000,          -38.4950000,          340.00,          TRUE),
    ('Camila Freitas Andrade',  'Goiânia',        'GO', 'Setor Bueno',      'Rua T-55',                 '820',  -16.7050000,          -49.2720000,          410.25,          TRUE),
    ('Eduardo Pacheco Silva',   'Florianópolis',  'SC', 'Campeche',         'Avenida Pequeno Príncipe', 'S/N',  -27.6780000,          -48.4900000,          520.00,          TRUE)
) AS v(proprietario, municipio, uf, bairro, rua, numero, latitude, longitude, area_m2, ativo)
WHERE NOT EXISTS (SELECT 1 FROM imovel);
