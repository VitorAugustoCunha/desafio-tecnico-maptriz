# WebGIS — cadastro de imóveis georreferenciados

Solução para o desafio técnico da **Maptriz Smart City** — auditoria, refatoração
e evolução de um cadastro multifinalitário de imóveis, com listagem preparada
para grande volume, mapa por viewport e geometria real sem sobreposição.

Autor: **Vitor Augusto**

| Camada | Tecnologia |
|--------|-----------|
| Backend | Java 21, Spring Boot 3.5.16, Maven |
| Banco | PostgreSQL 16 + **PostGIS 3.4**, migrations com Flyway |
| Frontend | Angular 22 (standalone, **zoneless**, signals), OpenLayers 10 |
| Testes | JUnit 5 + Testcontainers (PostGIS real) · Vitest + TestBed |
| Entrega | Docker multi-stage, Nginx, Docker Compose, GitHub Actions |

---

## Sumário

- [Como rodar](#como-rodar)
- [O que foi encontrado no código original](#o-que-foi-encontrado-no-código-original)
- [As 8 tarefas](#as-8-tarefas)
- [Arquitetura](#arquitetura)
- [Modelo de dados e migrations](#modelo-de-dados-e-migrations)
- [API](#api)
- [Rotas do frontend](#rotas-do-frontend)
- [Estratégia de cache da listagem](#estratégia-de-cache-da-listagem)
- [Volume e workers](#volume-e-workers)
- [Decisões GIS: CRS, SRID e a limitação do EPSG:31982](#decisões-gis-crs-srid-e-a-limitação-do-epsg31982)
- [Testes](#testes)
- [Resultados medidos](#resultados-medidos)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Limitações e próximos passos](#limitações-e-próximos-passos)

---

## Como rodar

### Com Docker (recomendado)

```bash
cp .env.example .env      # defina POSTGRES_PASSWORD
docker compose up --build
```

Abra **http://localhost:8080**.

O compose sobe banco → backend → frontend, cada um esperando o anterior ficar
**saudável de verdade** (`condition: service_healthy`). Não é preciso rodar
migration à mão: o Flyway cria o schema, carrega os dados legados e os migra na
primeira subida.

Apenas a porta do frontend é publicada. **O banco não fica exposto no host.**

### Sem Docker

Pré-requisitos: JDK 21, Node 20+, PostgreSQL 16 com a extensão PostGIS.

```bash
# 1. banco
createdb webgis
psql -d webgis -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# 2. backend  (http://localhost:8080)
cd backend
export WEBGIS_DB_URL=jdbc:postgresql://localhost:5432/webgis
export WEBGIS_DB_USER=webgis
export WEBGIS_DB_PASSWORD=suasenha
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. frontend (http://localhost:4200)
cd frontend
npm ci
npm start
```

O perfil `local` habilita CORS para `http://localhost:4200`. O dev-server faz
proxy de `/api` para `:8080` (`proxy.conf.json`), então o frontend usa os mesmos
caminhos relativos de produção.

> **Java 21 é obrigatório.** Se a sua máquina tiver outra versão, use
> `scripts/mvn-docker.ps1` (Windows), que roda o Maven em container com JDK 21 e
> monta o socket do Docker para o Testcontainers funcionar.

---

## O que foi encontrado no código original

A auditoria completa, com evidências reproduzidas contra a aplicação rodando,
está em **[docs/CODE_REVIEW.md](docs/CODE_REVIEW.md)** — 44 achados classificados
por gravidade.

O enunciado avisa: *"o que acontece na tela nem sempre é o que aconteceu no
banco"*. São **três** mentiras diferentes, e cada uma foi reproduzida:

**1. Injeção de SQL** — o `id` ia concatenado direto na consulta.

```
GET /api/imoveis/0%20or%201%3D1   →  HTTP 200, devolve o imóvel id=1
```

Um `DELETE` com predicado sempre-verdadeiro apagaria a base inteira.

**2. Perda silenciosa de dado** — limpar um campo numérico na tela apagava a
coordenada, e a API respondia sucesso.

```
antes   13 | Teste Alterado | -25.4000000 | -49.2000000 | 100.00
PUT com latitude/longitude/areaM2 nulos  →  HTTP 200 {"status":"ok"}
depois  13 | Teste Alterado |             |             |
```

Um campo de texto ausente virava a **string literal `"null"`** no banco
(comprimento 4). E `POST [1,2,3]` respondia `200` com corpo vazio, porque o
`ClassCastException` era engolido por `catch { return null; }`.

**3. A tabela mentia antes mesmo de salvar** — `editar(i)` fazia `this.form = i`,
atribuindo a **referência** do item da lista ao formulário. Digitar alterava a
linha da tabela na hora; clicar em *Cancelar* deixava a tela mostrando um valor
que nunca foi para o banco.

Além disso: `data.sql` com `sql.init.mode=always` **duplicava o seed a cada
subida** (13 → 25 imóveis após um restart), `CORS: *`, credenciais no
repositório, `ddl-auto=update`, nenhuma validação, `GET`/`DELETE` de id
inexistente respondendo `200`, e a listagem carregando a tabela inteira.

---

## As 8 tarefas

| # | Tarefa | Status | Onde verificar |
|---|--------|--------|----------------|
| 1 | Separar cadastro e listagem em duas páginas | ✅ | `/imoveis` e `/imoveis/novo` |
| 2 | Filtros por proprietário e município | ✅ | Filtrados **no servidor**, com índice trigrama |
| 3 | Página de edição **sem novo GET ao voltar** | ✅ | `ImoveisStore` + teste que prova por `HttpTestingController` |
| 4 | Proprietário como entidade, sem perder dados | ✅ | `V3__proprietario.sql` + teste de migração em duas etapas |
| 5 | Renomear valendo para todos os imóveis | ✅ | FK: uma linha alterada; teste com 3 imóveis |
| 6 | Listagem preparada para grande volume | ✅ | Medido com **500.012 imóveis** — [docs/PERFORMANCE.md](docs/PERFORMANCE.md) |
| 7 | Mapa (desejável) | ✅ | OpenLayers + consulta por viewport, com clustering no PostGIS |
| 8 | Geometria sem sobreposição (opcional, sênior) | ✅ | `POLYGON` em EPSG:31982, `409` atômico, teste de concorrência |

---

## Arquitetura

Diagramas (componentes, fluxo do requisito 3, sequência do lock espacial e
modelo de dados) em **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

```
.
├── backend/          Spring Boot — pacotes por funcionalidade
│   └── src/main/java/br/com/webgis/
│       ├── imovel/          controller, service, repository, mapper, DTOs
│       ├── proprietario/    entidade, CRUD, regra de deduplicação de nome
│       ├── gis/             geometria, mapa GeoJSON, worker de exportação
│       └── shared/          ProblemDetail, paginação, CORS, requestId
├── frontend/         Angular — core (api/state/ui) + features + shared
├── docs/             CODE_REVIEW, ARCHITECTURE, PERFORMANCE, DECISIONS, API.http
├── scripts/          seed de volume, Maven em container
└── compose.yaml
```

**Por que assim:** o controller só traduz HTTP; o service concentra regra e
transação; o repository só consulta. Nenhuma entidade JPA cruza a fronteira
HTTP — entra e sai `record`. As decisões que mudam a forma da solução estão em
**[docs/DECISIONS.md](docs/DECISIONS.md)** (10 ADRs).

---

## Modelo de dados e migrations

| Migration | O que faz |
|-----------|-----------|
| `V1__schema_inicial.sql` | Tabela `imovel` com `CHECK` de faixa geográfica, área positiva e UF |
| `V2__dados_legados.sql` | Os 12 imóveis originais, **com proprietário ainda em texto** |
| `V3__proprietario.sql` | Cria a entidade, deduplica, preenche a FK, **valida** e só então remove a coluna antiga |
| `V4__indices_listagem.sql` | `pg_trgm` + índices de filtro, ordenação e FK |
| `V5__postgis_geometria.sql` | PostGIS, `geom` (POLYGON 31982), `ponto` (POINT 4326, gerada), GiST, `webgis_retangulo` |

### A migração que não pode perder dado (tarefa 4)

A `V3` segue seis passos, e **valida antes de destruir**:

1. cria `proprietario`;
2. define a regra de normalização (`btrim` + colapso de espaços + `lower`);
3. insere os titulares distintos a partir dos textos existentes;
4. preenche `imovel.proprietario_id`;
5. **aborta a migration** se sobrar qualquer imóvel sem titular ou se o número
   de titulares criados for menor que o de nomes distintos na origem;
6. só então executa `DROP COLUMN proprietario`.

A regra **deliberadamente não remove acentos**: unir dois titulares distintos é
um erro irreversível (some um titular do cadastro), enquanto manter `José` e
`Jose` separados é corrigível depois por uma fusão explícita. Em cadastro
público, errar para o lado conservador é a escolha certa.

Imóveis com proprietário vazio ganham um titular explícito
`"Proprietário não informado"` — não são descartados nem bloqueiam a migração.

`MigracaoProprietarioTest` aplica o Flyway **até a V2**, injeta os casos que a
base real produz (mesmo nome com espaçamento diferente, mesmo nome em caixa
diferente, imóvel sem titular), aplica a `V3` e verifica: nenhum imóvel perdido,
nenhum órfão, coluna antiga removida, e as **três grafias de "Maria Aparecida
Souza" deduplicadas para um único titular com três imóveis**.

---

## API

Coleção pronta em **[docs/API.http](docs/API.http)** (VS Code REST Client /
IntelliJ), com o `curl` equivalente em cada bloco.

| Método | Rota | Resposta |
|--------|------|----------|
| `GET` | `/api/imoveis` | `200` — página filtrada e ordenada |
| `GET` | `/api/imoveis/{id}` | `200` · `404` se não existe |
| `POST` | `/api/imoveis` | `201` + `Location` + recurso · `400` · `409` conflito espacial |
| `PUT` | `/api/imoveis/{id}` | `200` com o recurso atualizado · `400` · `404` · `409` |
| `DELETE` | `/api/imoveis/{id}` | `204` · `404` |
| `GET` | `/api/proprietarios` | `200` — página com contagem de imóveis |
| `GET` | `/api/proprietarios/{id}` | `200` · `404` |
| `POST` | `/api/proprietarios` | `201` · `409` nome duplicado |
| `PUT` | `/api/proprietarios/{id}` | `200` — renomeia · `409` |
| `DELETE` | `/api/proprietarios/{id}` | `204` · `409` se ainda tem imóveis |
| `GET` | `/api/mapa/imoveis` | `200` — GeoJSON recortado por bbox |
| `POST` | `/api/exportacoes/geojson` | `202` + `Location` · **`503`** + `Retry-After` |
| `GET` | `/api/exportacoes/{id}` | `200` — status |
| `GET` | `/api/exportacoes/{id}/arquivo` | `200` — GeoJSON |
| `DELETE` | `/api/exportacoes/{id}` | `204` — cancela |

**Parâmetros da listagem:** `proprietarioId`, `proprietarioNome`, `municipio`,
`ativo`, `pagina`, `tamanho` (limitado no servidor), `ordenarPor`
(`id`, `municipio`, `area`, `criado_em`, `proprietario` — whitelist), `direcao`.

### Contrato de erro

Todo erro sai em **Problem Details (RFC 9457)**, com `type` estável:

```json
{
  "type": "urn:webgis:problema:validacao",
  "title": "Dados invalidos",
  "status": 400,
  "detail": "Um ou mais campos do corpo da requisicao sao invalidos.",
  "erros": [
    { "campo": "latitude", "mensagem": "a latitude deve estar entre -90 e 90" },
    { "campo": "uf", "mensagem": "use a sigla de 2 letras, por exemplo SP" }
  ]
}
```

O conflito espacial devolve o **id do imóvel conflitante**, e a tela usa isso
para oferecer "ver o imóvel N".

---

## Rotas do frontend

| Rota | Tela |
|------|------|
| `/imoveis` | Listagem paginada, filtrável e ordenável |
| `/imoveis/novo` | Cadastro |
| `/imoveis/:id/editar` | Edição |
| `/proprietarios` | Titulares com contagem de imóveis |
| `/proprietarios/:id` | Detalhe, renomear e imóveis do titular |
| `/mapa` | Mapa OpenLayers com consulta por viewport |
| `**` | 404 própria |

Todas com carregamento sob demanda: quem não abre o mapa **não baixa o
OpenLayers** (940 kB isolados nesse chunk).

---

## Estratégia de cache da listagem

O requisito 3 proíbe nova requisição ao voltar da edição. Quem garante isso é o
`ImoveisStore`, que guarda uma consulta por vez identificada por uma chave
normalizada (filtros + página + tamanho + ordenação).

| Situação | Comportamento |
|----------|---------------|
| Volta da edição, mesma consulta | **Reaproveita — nenhuma requisição** |
| Muda filtro, página ou ordenação | Nova consulta (são dados diferentes) |
| Entra pela URL ou atualiza o navegador | Nova consulta (o store nasce vazio) |
| Depois de **editar** | Corrige o item em memória, imutavelmente |
| Depois de **criar** ou **excluir** | Invalida e recarrega |
| Passados 5 minutos | Nova consulta |

Depois do `PUT`, o item é substituído pelo recurso que a própria resposta
devolveu — por isso o `PUT` retorna o objeto atualizado.

Detalhe que faz o requisito funcionar: a volta **preserva os query params**. A
chave inclui os filtros; voltar sem eles seria outra consulta e dispararia um GET.

Criar e excluir invalidam porque mudam quais imóveis caem em qual página e o
total — qualquer correção local divergiria do servidor.

---

## Volume e workers

### Primeiro o banco, depois os workers

A listagem **nunca** carrega tudo: filtros e paginação no servidor, projeção
enxuta, `tamanho` limitado por configuração e ordenação por whitelist (que
também impede ordenar por campo arbitrário). Índices em `V4`, incluindo
`pg_trgm` para a busca parcial case-insensitive — sobre `lower(coluna)`, porque é
essa a expressão que a aplicação gera.

**Um pool de threads não conserta uma consulta que varre 500 mil linhas.** Os
workers são complementares: movem trabalho inevitável para fora do caminho da
requisição.

### Backend — `gisWorkerExecutor`

Pool dedicado, separado das threads que atendem o CRUD, com o caso de uso
implementado de ponta a ponta: **exportação GeoJSON em lote**.

- fila **limitada** + `AbortPolicy` → `503` + `Retry-After` quando satura;
- leitura em lotes por keyset (`id > ultimoId`), escrevendo direto no arquivo —
  exportar 500 mil imóveis usa a mesma memória que exportar 500;
- cada lote abre a **própria transação** dentro da thread do worker, nunca a
  herda de quem submeteu;
- MDC propagado por `TaskDecorator`: o `requestId` da requisição aparece nas
  linhas de log do worker;
- cancelamento entre lotes, timeout e shutdown gracioso;
- métricas expostas como `MeterBinder`.

Fila ilimitada não é "aceitar tudo": é adiar a falha até faltar memória,
perdendo todas as tarefas de uma vez.

**A validação de sobreposição continua síncrona e transacional** — consistência
crítica não vai para processamento eventual (ADR-006).

### Frontend — Web Worker GIS

Cuida do pipeline CPU-bound sobre as feições já recebidas: validação e
normalização de coordenadas, achatamento em `Float64Array`, estatísticas e
agrupamento por município.

- protocolo de mensagens **tipado** nos dois lados;
- `requestId` para **descartar resposta obsoleta** — arrastar o mapa gera pedidos
  em sequência, e um resultado antigo que chegue depois não pode sobrescrever o
  atual;
- `Float64Array` **transferido**, não copiado;
- cancelamento lógico, erro serializável e **fallback síncrono** para SSR e teste;
- funções puras fora do worker, testadas direto.

**O que ele não faz:** clustering (fica no PostGIS, onde não exige baixar tudo
antes) nem chamadas HTTP. Ver ADR-007.

---

## Decisões GIS: CRS, SRID e a limitação do EPSG:31982

**Duas formas de definir o lote**, excludentes entre si (ADR-011):

| Modo | Entrada | Área e ponto |
|------|---------|--------------|
| **Ponto + dimensões** (tarefa 8) | centro + largura/comprimento | área = largura × comprimento |
| **Desenho no mapa** | polígono traçado vértice a vértice | derivados do polígono pelo PostGIS |

O formulário embute um mapa com as duas abas. No modo dimensões, clicar
posiciona o centro e o retângulo aparece na hora — calculado no cliente com a
**mesma projeção do servidor** (o frontend registra o EPSG:31982 via proj4), de
modo que a prévia é a geometria que será gravada, não uma ilustração. No modo
desenho, a área e o ponto do imóvel passam a ser calculados pelo PostGIS
(`ST_Area` e `ST_Centroid`) por cima do que o cliente enviou — guardar uma área
que contradiz o polígono seria manter duas versões da mesma verdade.

As duas formas compartilham o mesmo advisory lock e a mesma checagem de
interseção: um desenho e um retângulo disputando a mesma área se enxergam.

**Convenção adotada:** latitude/longitude são o **centro** do lote. Metade da
largura para cada lado no eixo X, metade do comprimento em cada lado no eixo Y,
em metros no plano projetado. Há teste conferindo que o centroide do polígono
coincide com o ponto informado.

**A projeção 4326 → 31982 é feita pelo PostGIS**, na função SQL
`webgis_retangulo`, e não em Java. Motivo: quem compara (`ST_Intersects`), valida
(`ST_IsValid`) e indexa (GiST) é o PostGIS — calcular a projeção em outra
biblioteca criaria duas implementações da mesma transformação, e divergência na
última casa decimal significa aceitar um lote que deveria ser recusado.

**Interseção, não sobreposição.** O enunciado diz "intersecta **ou** sobrepõe",
então **encostar a borda já é conflito**. Por isso `ST_Intersects` e não
`ST_Overlaps` — este último devolveria falso justamente para lotes que apenas se
tocam, e também para um lote inteiramente dentro de outro. Há teste que translada
um retângulo exatamente pela sua largura e confirma o conflito.

**Concorrência.** "Consultar conflito" e "gravar" são dois passos; entre eles,
outra transação pode inserir no mesmo lugar. A solução é um
`pg_advisory_xact_lock` tomado antes da verificação, liberado no commit ou
rollback. `ConcorrenciaGeometriaTest` dispara **6 cadastros simultâneos do mesmo
retângulo** e exige que exatamente 1 seja aceito.

### A limitação, dita em voz alta

**EPSG:31982 é SIRGAS 2000 / UTM zona 22S** — meridiano central 51°W. Cobre bem
Sul e Sudeste; quanto mais o imóvel se afasta da zona, maior a distorção de
distância e de área. **O enunciado fixa esse SRID e ele foi mantido**; as
alternativas (SRID por zona UTM, ou um CRS único como EPSG:5880) estão discutidas
no ADR-004. Trocar o SRID pedido em silêncio seria pior que a limitação.

**Imóveis legados** têm `geom` nulo: aparecem no mapa como ponto e **não
participam** da checagem de conflito — não há geometria para comparar. Ao editar
um deles informando largura e comprimento, o polígono é gerado. Remover as
dimensões apaga o polígono e libera a área.

---

## Testes

```bash
# Backend — 106 testes + cobertura (exige Docker para o Testcontainers)
cd backend && ./mvnw verify

# Frontend — 84 testes
cd frontend && npm run test:ci

# Sem JDK 21 na máquina (Windows): Maven em container
./scripts/mvn-docker.ps1 verify
```

Os testes de integração rodam contra **PostgreSQL com PostGIS de verdade**, nunca
H2: `ST_Intersects`, `ST_Transform`, coluna gerada, GiST, `pg_trgm` e advisory
lock não existem em banco em memória. Um teste que passa no H2 e não no Postgres
não prova nada.

**Destaques da suíte**, todos verificáveis:

- migração de proprietários provando ausência de perda, com dedup de três
  grafias e imóvel sem titular;
- contraprova de cada evidência do code review (404 em vez de `200` vazio, `400`
  em vez de injeção, nome com apóstrofo gravado, `PUT` sem campo não apaga dado);
- polígono em SRID 31982 com área conferida pelo PostGIS;
- **6 cadastros concorrentes na mesma área → exatamente 1 aceito**, sincronizados
  por `CyclicBarrier` (sem `sleep`);
- saturação do pool GIS → `503`, com `CountDownLatch` (sem `sleep`);
- **retorno da edição sem novo GET**, provado por `HttpTestingController`;
- descarte de resposta obsoleta do Web Worker, respondendo fora de ordem de
  propósito.

---

## Resultados medidos

Método, ambiente e limitações em **[docs/PERFORMANCE.md](docs/PERFORMANCE.md)**.
Nenhum número aqui é estimado.

**Testes**

| Suíte | Testes | Resultado |
|-------|-------:|-----------|
| Backend (JUnit 5 + Testcontainers) | **120** | ✅ todos passando |
| Frontend (Vitest + TestBed) | **95** | ✅ todos passando |
| Cobertura de linhas (JaCoCo) | **85,8%** | domínio acima de 95% |

**Efeito dos índices** — `EXPLAIN (ANALYZE, BUFFERS)` com **500.012 imóveis**:

| Consulta | Sem índice | Com índice | Ganho |
|----------|-----------:|-----------:|------:|
| Listagem filtrada por município | 279,2 ms | **39,1 ms** | 7,1× |
| Listagem filtrada por proprietário | 138,6 ms | **3,3 ms** | 42× |
| `COUNT` da paginação | 154,1 ms | **26,9 ms** | 5,7× |
| Filtro por `proprietario_id` | 67,3 ms | **0,125 ms** | 538× |
| Mapa por viewport (GiST) | 96,7 ms | **0,71 ms** | 136× |
| Ordenação por município | 185,2 ms | **0,027 ms** | 6.859× |

**Latência HTTP com 500.012 imóveis** (p50, ponta a ponta pelo Nginx):

| Endpoint | p50 | Resposta |
|----------|----:|---------:|
| `GET /api/imoveis` | 89,7 ms | 4,9 KB |
| `GET /api/imoveis?municipio=…` | 114,3 ms | 4,9 KB |
| `GET /api/proprietarios` | 114,4 ms | 1,3 KB |
| `GET /api/mapa/imoveis` (cidade) | 66,2 ms | 8,1 KB |

**A resposta da listagem tem ~5 KB com 12 ou com 500 mil imóveis cadastrados.**
É o ponto da tarefa 6.

**Um defeito encontrado pela própria medição:** `GET /api/proprietarios` estava
em 573 ms porque minha consulta agregava a tabela inteira de imóveis
(`LEFT JOIN` + `GROUP BY`) para devolver 20 linhas. Trocada por subconsulta
correlacionada: **573 ms → 114 ms**.

**Frontend:** bundle inicial de **285,9 kB** (78,9 kB transferido); o OpenLayers
(940 kB) só é baixado ao abrir `/mapa`.

---

## Variáveis de ambiente

Nenhum segredo no repositório. Veja `.env.example`.

| Variável | Padrão | Para quê |
|----------|--------|----------|
| `POSTGRES_DB` / `POSTGRES_USER` | `webgis` | Banco do compose |
| `POSTGRES_PASSWORD` | — | **Obrigatória**: o compose falha sem ela, de propósito |
| `WEBGIS_PORTA` | `8080` | Porta publicada no host |
| `WEBGIS_DB_URL` / `_USER` / `_PASSWORD` | — | Conexão do backend |
| `WEBGIS_CORS_ORIGINS` | vazio | Origens autorizadas; vazio no compose (mesma origem) |
| `WEBGIS_LOG_LEVEL` | `INFO` | Nível de log da aplicação |
| `WEBGIS_PAGINA_TAMANHO_MAXIMO` | `100` | Teto do `tamanho` da página |
| `WEBGIS_MAPA_LIMITE_FEICOES` | `2000` | Teto de feições por viewport |
| `WEBGIS_MAPA_ZOOM_DETALHE` | `12` | Abaixo disso o mapa recebe agregados |
| `WEBGIS_GIS_POOL_NUCLEO` / `_MAXIMO` / `_FILA` | `2` / `4` / `8` | Dimensionamento do pool GIS |

---

## Limitações e próximos passos

Ditas antes de alguém perguntar.

1. **Não há autenticação nem autorização.** O enunciado não pediu, e não inventei
   um modelo de permissão sem requisito. Em um cadastro público real, este é o
   primeiro item da próxima etapa — e o `@RestControllerAdvice` e o filtro de
   `requestId` já estão prontos para conviver com Spring Security.
2. **Clusterização em viewport regional custa ~750 ms** com o dado sintético
   (grade uniforme pelo país inteiro, o pior caso possível para agregação
   espacial). A solução estrutural é uma tabela de grade materializada por nível
   de zoom; não implementei porque seria complexidade sem problema medido em uso
   real — o mapa de cidade responde em 66 ms.
3. **A exportação assume uma instância**: registro em memória e arquivos em disco
   local. Com réplicas, o estado iria para banco/Redis e os arquivos para object
   storage.
4. **Paginação por offset** na listagem. Escolha consciente (a tela precisa de
   total e navegação por página); o keyset já está implementado na exportação.
   Se o volume crescer 10×, a listagem migra para keyset — ADR-003.
5. **Sem benchmark conclusivo do Web Worker.** Nos volumes que consegui medir, o
   pipeline roda em poucos milissegundos e o worker não é necessário. Preferi
   registrar isso a fabricar um número.
6. **EPSG:31982 limita a validade nacional** (UTM 22S). Mantido porque o
   enunciado fixa; alternativas no ADR-004.
7. **Um teste de acessibilidade automatizado** (axe) e testes E2E com navegador
   real (Playwright) fechariam a cobertura. Hoje a acessibilidade foi feita por
   construção — labels reais, foco visível, `aria-live`, `aria-sort`, `<dialog>`
   nativo — e verificada manualmente, não por ferramenta.
8. **Sem observabilidade além de métricas e log estruturado.** O passo natural é
   OpenTelemetry com tracing distribuído.

---

## Documentação

| Documento | Conteúdo |
|-----------|----------|
| [docs/CODE_REVIEW.md](docs/CODE_REVIEW.md) | Auditoria do código original, 44 achados com evidência reproduzida |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Diagramas de componentes, fluxos e modelo de dados |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Método, ambiente, limitações e números medidos |
| [docs/DECISIONS.md](docs/DECISIONS.md) | 10 ADRs com alternativas descartadas e custos assumidos |
| [docs/API.http](docs/API.http) | Coleção HTTP executável, com `curl` equivalente |
