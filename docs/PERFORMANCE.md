# Performance — método e resultados reais

Todos os números deste documento foram **medidos**, não estimados. Onde não foi
possível medir, está escrito explicitamente que é hipótese.

## Ambiente

| Item | Valor |
|------|-------|
| Host | Windows 10 Pro, 16 vCPU, 32 GB RAM |
| Docker | 29.6.2 (Docker Desktop 4.84.0) |
| Banco | `postgis/postgis:16-3.4` — PostgreSQL 16.4, container com 4 vCPU |
| Backend | Java 21 (Temurin), Spring Boot 3.5.16, container do compose |
| Frontend | Angular 22.1.1, servido por Nginx no compose |
| Caminho medido | `curl`/`Invoke-WebRequest` → Nginx `:8080` → proxy `/api` → backend |

**Configuração do PostgreSQL: a padrão da imagem**, sem tuning
(`shared_buffers=128MB`, `work_mem=4MB`, `effective_cache_size=4GB`,
`max_parallel_workers_per_gather=2`). Isso é deliberado: os ganhos abaixo vêm de
modelagem e índice, não de configuração de banco. Um servidor real teria
`shared_buffers` bem maior e vários dos números melhorariam sozinhos.

### Limitações da medição — leia antes de usar estes números

1. **Máquina de desenvolvimento, não servidor.** Há IDE, navegador e Docker
   Desktop competindo por CPU. Os valores servem para comparar *entre si*, não
   como capacidade de produção.
2. **Banco e aplicação na mesma máquina**, então a latência de rede é ~0. Em
   produção, some o RTT real.
3. **Dados sintéticos.** O gerador espalha imóveis numa grade regular por todo o
   território. Isso é o **pior caso** para clusterização espacial (ver seção do
   mapa) e o **melhor caso** para o filtro de município (cardinalidade uniforme).
   Cadastro real é concentrado em manchas urbanas.
4. **p50/p95 sobre 12 repetições** após uma chamada de aquecimento. Amostra
   pequena; o p95 tem incerteza alta.

## Volume usado

```bash
docker compose exec -T banco psql -U webgis -d webgis \
  -v imoveis=500000 -v proprietarios=50000 -v municipios=1200 \
  -f - < scripts/seed-volume.sql
```

| Métrica | Valor |
|---------|-------|
| Imóveis | **500.012** |
| Proprietários | **50.012** |
| Municípios distintos | **1.210** |
| Tamanho da tabela `imovel` (com índices) | **235 MB** |
| Tempo de carga | 61 s |

O enunciado fala em "mais de mil municípios — e muito mais imóveis do que isso".
500 mil imóveis em 1.210 municípios cobre esse cenário com folga.

## Resultado 1 — efeito dos índices

`EXPLAIN (ANALYZE, BUFFERS)` sobre as consultas que sustentam a listagem, com e
sem os índices da migration `V4`. O "sem" foi obtido derrubando os índices,
medindo e recriando — na mesma sessão e com o mesmo dado.

| # | Consulta | Sem índice | Com índice | Ganho | Plano com índice |
|---|----------|-----------:|-----------:|------:|------------------|
| A | Listagem filtrada por município (`ILIKE` parcial) + join + página | 279,2 ms | **39,1 ms** | **7,1×** | Bitmap Index Scan em `idx_imovel_municipio_trgm` |
| B | Listagem filtrada por nome do proprietário (`ILIKE` parcial) | 138,6 ms | **3,3 ms** | **42×** | Bitmap Index Scan em `idx_proprietario_nome_trgm` + `idx_imovel_proprietario_id` |
| C | `COUNT(*)` que sustenta a paginação (mesmo filtro de A) | 154,1 ms | **26,9 ms** | **5,7×** | Bitmap Index Scan + Index Only Scan na PK |
| D | Filtro por `proprietario_id` (página do proprietário) | 67,3 ms | **0,125 ms** | **538×** | Bitmap Index Scan em `idx_imovel_proprietario_id` |
| E | Mapa por viewport (`&&` + GiST) | 96,7 ms | **0,71 ms** | **136×** | Index Scan em `idx_imovel_ponto_gist` |
| G | Ordenação por município com desempate por id | 185,2 ms | **0,027 ms** | **6.859×** | Index Scan em `idx_imovel_municipio_id`, sem sort |

Sem índice, **todas** viram `Parallel Seq Scan` sobre as 500 mil linhas — que é
exatamente o que o código original fazia em toda requisição, só que sem `LIMIT`.

### Por que o índice trigrama, e não um B-tree

A busca da listagem é parcial e sem diferenciar maiúsculas
(`lower(municipio) LIKE lower('%texto%')`). O curinga à esquerda impede
qualquer B-tree de ser usado — o índice B-tree ordena por prefixo. `pg_trgm`
com GIN indexa trigramas e resolve o curinga dos dois lados.

Detalhe que custou uma medição para descobrir: o índice precisa ser sobre
**`lower(municipio)`**, e não sobre `municipio`. A aplicação gera
`lower(coluna) LIKE lower(?)`, e um índice sobre a coluna crua simplesmente não
é considerado pelo planejador.

### Custo dos índices

| Índice | Tamanho |
|--------|--------:|
| `idx_imovel_ponto_gist` | 20 MB |
| `idx_imovel_area_id` | 20 MB |
| `idx_imovel_geom_gist` | 20 MB |
| `idx_imovel_criado_id` | 20 MB |
| `idx_imovel_municipio_id` | 19 MB |
| `idx_imovel_municipio_trgm` | 14 MB |
| `idx_imovel_ativo_id` (parcial) | 11 MB |
| `idx_imovel_proprietario_id` | 4,6 MB |
| `idx_proprietario_nome_trgm` | 1,5 MB |

~130 MB de índice para 235 MB de tabela. É uma troca consciente: este cadastro
lê muito mais do que escreve, e o custo de manutenção no `INSERT` é irrelevante
perto de transformar 279 ms em 39 ms em toda listagem.

## Resultado 2 — latência dos endpoints HTTP

Medida de ponta a ponta, atravessando Nginx e backend, com 500.012 imóveis.

| Endpoint | p50 | p95 | Resposta |
|----------|----:|----:|---------:|
| `GET /api/imoveis?tamanho=20` | 89,7 ms | 114,9 ms | 4,9 KB |
| `GET /api/imoveis?municipio=Municipio 0500` | 114,3 ms | 137,1 ms | 4,9 KB |
| `GET /api/imoveis?proprietarioNome=Titular 012345` | 41,9 ms | 44,6 ms | 2,5 KB |
| `GET /api/imoveis?pagina=500` (offset profundo) | 116,4 ms | 137,3 ms | 4,9 KB |
| `GET /api/imoveis?tamanho=100` (máximo) | 143,2 ms | 153,9 ms | 23,8 KB |
| `GET /api/imoveis?ordenarPor=area&direcao=desc` | 81,6 ms | 84,1 ms | 4,9 KB |
| `GET /api/proprietarios?tamanho=20` | **114,4 ms** | 131,0 ms | 1,3 KB |
| `GET /api/mapa/imoveis` (viewport de cidade, zoom 14) | 66,2 ms | 73,5 ms | 8,1 KB |
| `GET /api/mapa/imoveis` (viewport regional, zoom 6) | 750,4 ms | 780,3 ms | 118,6 KB |

**A resposta da listagem tem ~5 KB em qualquer volume.** É o ponto central da
tarefa 6: com 12 ou com 500 mil imóveis cadastrados, a página transporta 20
linhas. No código original, a resposta crescia junto com a tabela.

## Resultado 3 — um defeito encontrado *pela* medição

A primeira rodada mostrou `GET /api/proprietarios` em **p50 573 ms, p95 1.111 ms** —
fora da curva de todo o resto. A causa estava na minha própria consulta:

```sql
-- antes: agrega a tabela INTEIRA de imóveis para devolver 20 linhas
SELECT p.id, p.nome, COUNT(i.id)
  FROM Proprietario p LEFT JOIN Imovel i ON i.proprietario = p
 GROUP BY p.id, p.nome
```

Trocada por uma subconsulta correlacionada, que conta apenas os 20 titulares da
página, cada um pelo `idx_imovel_proprietario_id`:

```sql
-- depois: uma contagem indexada por linha exibida
SELECT p.id, p.nome, (SELECT COUNT(i.id) FROM Imovel i WHERE i.proprietario = p)
  FROM Proprietario p
```

| | p50 | p95 |
|---|----:|----:|
| Antes (`GROUP BY` sobre o join) | 572,9 ms | 1.110,9 ms |
| Depois (subconsulta correlacionada) | **114,4 ms** | **131,0 ms** |
| | **5,0× melhor** | **8,5× melhor** |

Continua sendo **uma** ida ao banco — não virou N+1. N+1 seria a aplicação
emitir uma consulta por linha, com ida e volta pela rede a cada uma.

## Resultado 4 — o que ainda não está bom

Honestidade vale mais que uma tabela toda verde.

### Clusterização em viewport regional: 750 ms

`GET /api/mapa/imoveis` com zoom 6 sobre 20°×20° custa 750 ms. O `EXPLAIN`
mostra o motivo:

```
Sort Method: external merge  Disk: 2960kB
->  Parallel Seq Scan on imovel i (rows=40643 loops=3)
Execution Time: 1597.930 ms
```

O `GROUP BY ST_SnapToGrid(...)` precisa ordenar ~122 mil pontos e estoura o
`work_mem` de 4 MB, indo para disco.

Três observações honestas:

1. **O dado sintético é o pior caso possível.** O gerador distribui imóveis numa
   grade regular por todo o Brasil, então quase toda célula da grade recebe
   pontos e o número de grupos explode. Cadastro real concentra imóveis em
   manchas urbanas: poucas células com muitos pontos, que é o caso em que a
   agregação compensa.
2. **`work_mem=4MB` é o padrão da imagem.** Só isso já resolveria boa parte —
   mas preferi não maquiar o número mexendo na configuração do banco.
3. **A solução estrutural não é ajuste fino**, é pré-agregar: uma tabela de
   grade materializada por nível de zoom, atualizada periodicamente. Está nos
   próximos passos, não implementada — seria complexidade sem problema medido em
   uso real, e o mapa de cidade (o caso comum) responde em 66 ms.

### Paginação por offset profundo

`pagina=500` (offset 10.000) custa 116 ms contra 90 ms na primeira página.
A degradação existe e é conhecida: o banco descarta as 10 mil linhas anteriores.

Optei por **manter a paginação por offset** com `COUNT`, e não migrar para
cursor, porque:

- a UI precisa de "X imóveis encontrados" e de navegação por número de página —
  é o que um operador de cadastro usa para saber o tamanho do resultado;
- o `COUNT` filtrado custa 27 ms com índice, não é o gargalo;
- o offset profundo só é alcançável clicando "próxima" 500 vezes; na prática o
  operador filtra em vez de paginar até lá;
- `size` é limitado por configuração, então não há como pedir tudo de uma vez.

Se o volume crescer uma ordem de grandeza, a saída é paginação por keyset
(`WHERE id > :ultimoId`), que já está implementada na exportação em lote
(`MapaRepository.loteParaExportacao`) — lá não há UI de páginas e o keyset é a
escolha certa. O trade-off está registrado em `docs/DECISIONS.md`, ADR-003.

## Frontend

| Métrica | Valor |
|---------|------:|
| Bundle inicial (produção) | **285,9 kB** (78,9 kB transferido) |
| Chunk do mapa (OpenLayers), carregado sob demanda | 940,7 kB |
| Chunk do Web Worker GIS | 4,6 kB |
| Tempo de build de produção | 4,4 s |

O OpenLayers é a maior dependência do projeto e **não entra no bundle inicial**:
está isolado na rota `/mapa` por `loadComponent`. Quem só usa a listagem nunca
baixa 940 kB.

### Web Worker — o que foi e o que não foi medido

O pipeline expõe o tempo de processamento na própria tela do mapa
(`Pipeline: N ms (Web Worker)`), então dá para conferir ao usar o sistema.

Nos viewports medidos aqui o lote é pequeno (dezenas a poucos milhares de
feições) e o pipeline roda em poucos milissegundos — **nesse tamanho o worker
não é necessário**. Ele existe para o caso em que o lote chega ao teto de 2.000
feições com polígonos, quando normalizar, validar e achatar coordenadas passa a
custar dezenas de milissegundos na thread que também desenha o mapa.

**Não tenho um benchmark comparativo com número grande o suficiente para ser
conclusivo**, e não vou inventar um. O que está medido e é verificável:

- as funções puras do pipeline são testadas fora do worker (13 testes);
- o protocolo descarta resposta obsoleta, com teste que responde fora de ordem
  de propósito;
- o `Float64Array` de coordenadas é **transferido** e não copiado, o que elimina
  a cópia estrutural do `postMessage` — o ganho cresce linearmente com o lote;
- há fallback síncrono, exercitado em teste, para SSR e ambiente sem `Worker`.

Ver `docs/DECISIONS.md`, ADR-007, para por que o worker **não** faz clustering.

## Como reproduzir

```bash
docker compose up -d --wait
docker compose exec -T banco psql -U webgis -d webgis -f - < scripts/seed-volume.sql
docker compose exec -T banco psql -U webgis -d webgis -c "EXPLAIN (ANALYZE, BUFFERS) SELECT ..."
```

O script de medição de latência HTTP usado está descrito na seção "Resultado 2":
12 repetições por endpoint, após uma chamada de aquecimento, medindo p50 e p95.
