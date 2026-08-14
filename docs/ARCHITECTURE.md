# Arquitetura

## Visão geral

```mermaid
flowchart LR
    Navegador["Navegador<br/>Angular 22 (zoneless)"]

    subgraph Compose["docker compose"]
        Nginx["frontend<br/>Nginx :8080<br/>SPA + proxy /api"]
        API["backend<br/>Spring Boot 3.5 / Java 21"]
        DB[("banco<br/>PostgreSQL 16 + PostGIS 3.4")]
    end

    Navegador -->|"HTTP :8080"| Nginx
    Nginx -->|"/api/*"| API
    API -->|"JDBC"| DB

    Navegador -.->|"tiles"| OSM["OpenStreetMap"]

    style Compose fill:#f4f6f8,stroke:#d8dee6
```

Só a porta do frontend é publicada. Backend e banco existem apenas na rede
interna do compose — o banco não fica exposto no host.

## Backend — organização por funcionalidade

```mermaid
flowchart TB
    subgraph imovel["imovel"]
        IC["ImovelController<br/>HTTP, status, validação"]
        IS["ImovelService<br/>regras + transação"]
        IR["ImovelRepository<br/>JPA + Specification"]
        IM["ImovelMapper<br/>DTO ↔ domínio"]
    end

    subgraph proprietario["proprietario"]
        PC["ProprietarioController"]
        PS["ProprietarioService"]
        PR["ProprietarioRepository"]
        NN["NomeNormalizador<br/>regra de deduplicação"]
    end

    subgraph gis["gis"]
        GS["GeometriaService<br/>advisory lock + conflito"]
        GR["GeometriaRepository<br/>ST_Intersects / ST_IsValid"]
        MS["MapaService<br/>viewport + clustering"]
        MR["MapaRepository"]
        EX["ExportacaoGeoJsonService<br/>pool + chunking"]
    end

    subgraph shared["shared"]
        EH["ApiExceptionHandler<br/>ProblemDetail RFC 9457"]
        PG["PaginaResponse"]
        RF["RequestIdFilter (MDC)"]
    end

    IC --> IS --> IR
    IS --> IM
    IS --> GS --> GR
    IS -.->|"localizar ou criar"| PS
    PC --> PS --> PR
    PS --> NN
    MS --> MR
    EX --> MR

    style shared fill:#f8fafc,stroke:#d8dee6
```

**Regra que orienta o corte:** o controller só traduz HTTP; o service concentra
regra e transação; o repository só sabe consultar. Nenhuma entidade JPA atravessa
a fronteira HTTP — entra e sai `record`.

### Estrutura de pastas (resumida)

```
backend/src/main/java/br/com/webgis/
├── imovel/            Imovel, Controller, Service, Repository, Mapper,
│   └── dto/           Specs, OrdenacaoImovel (whitelist)
├── proprietario/      entidade, CRUD, NomeNormalizador
│   └── dto/
├── gis/               geometria, mapa, GeoJSON
│   ├── dto/           Feicao, ColecaoDeFeicoes, propriedades seladas
│   └── worker/        pool GIS, exportação em lote, backpressure
└── shared/
    ├── config/        CORS por configuração
    ├── error/         exceções de domínio + ApiExceptionHandler
    └── web/           PaginaResponse, PaginacaoProperties, RequestIdFilter

backend/src/main/resources/db/migration/
├── V1__schema_inicial.sql       tabela imovel + CHECKs de domínio
├── V2__dados_legados.sql        12 imóveis com proprietário em TEXTO
├── V3__proprietario.sql         entidade + migração sem perda + FK
├── V4__indices_listagem.sql     pg_trgm, índices de filtro e ordenação
└── V5__postgis_geometria.sql    PostGIS, geom/ponto, GiST, webgis_retangulo
```

## Frontend — camadas

```mermaid
flowchart TB
    subgraph rotas["rotas (lazy)"]
        L["/imoveis"]
        N["/imoveis/novo"]
        E["/imoveis/:id/editar"]
        P["/proprietarios"]
        D["/proprietarios/:id"]
        M["/mapa"]
    end

    subgraph estado["core/state"]
        ST["ImoveisStore<br/>signals + política de cache"]
    end

    subgraph dados["core/api"]
        IA["ImovelApiService"]
        PA["ProprietarioApiService"]
        MA["MapaApiService"]
    end

    subgraph worker["features/mapa/gis"]
        WC["GisWorkerClient<br/>requestId, cancelamento, fallback"]
        WK["gis.worker.ts"]
        PP["gis-pipeline.ts<br/>funções puras (testadas fora)"]
    end

    L --> ST --> IA
    N --> IA
    E --> IA
    E -.->|"PUT devolve o recurso"| ST
    P --> PA
    D --> PA
    D --> IA
    M --> MA
    M --> WC --> WK --> PP

    style estado fill:#f8fafc,stroke:#d8dee6
```

Nenhum componente injeta `HttpClient`. Toda URL vem de `APP_CONFIG` (`/api`),
resolvida por proxy — não há host literal no código.

## O fluxo que o requisito 3 exige

```mermaid
sequenceDiagram
    participant U as Usuário
    participant L as Listagem
    participant S as ImoveisStore
    participant F as Formulário
    participant A as API

    U->>L: abre /imoveis
    L->>S: garantirCarregado(consulta)
    S->>A: GET /api/imoveis?...
    A-->>S: página
    S-->>L: itens (cache: chave da consulta)

    U->>F: Editar (preserva query params)
    F->>A: GET /api/imoveis/2
    A-->>F: imóvel

    U->>F: Salvar
    F->>A: PUT /api/imoveis/2
    A-->>F: imóvel atualizado
    F->>S: aplicarImovelAtualizado(imóvel)
    Note over S: troca o item de forma imutável
    F->>L: navega de volta (mesmos query params)
    L->>S: garantirCarregado(mesma consulta)
    Note over S,A: chave igual + cache válido<br/>NENHUMA requisição
    S-->>L: itens já corrigidos
```

O teste `edicao-sem-novo-get.spec.ts` percorre exatamente esse caminho e prova
por `HttpTestingController` que nenhum GET da listagem foi emitido no retorno.

## Como a não sobreposição é garantida

```mermaid
sequenceDiagram
    participant A as Transação A
    participant B as Transação B
    participant PG as PostgreSQL

    A->>PG: BEGIN
    A->>PG: pg_advisory_xact_lock(chave)
    B->>PG: BEGIN
    B->>PG: pg_advisory_xact_lock(chave)
    Note over B,PG: B espera

    A->>PG: INSERT imovel (sem geom)
    A->>PG: ST_IsValid + ST_Intersects → sem conflito
    A->>PG: UPDATE geom = webgis_retangulo(...)
    A->>PG: COMMIT
    Note over PG: lock liberado no commit

    Note over B,PG: B acorda e já enxerga a geometria de A
    B->>PG: ST_Intersects → CONFLITO
    B->>PG: ROLLBACK
    Note over B: HTTP 409 + idImovelConflitante
```

Detalhe: o lock é `xact`, liberado automaticamente no commit **ou no rollback** —
não há caminho em que ele fique preso.

## Modelo de dados

```mermaid
erDiagram
    PROPRIETARIO ||--o{ IMOVEL : possui

    PROPRIETARIO {
        bigint id PK
        varchar nome
        varchar nome_normalizado UK "chave de deduplicação"
        timestamptz criado_em
        timestamptz atualizado_em
    }

    IMOVEL {
        bigint id PK
        bigint proprietario_id FK
        varchar municipio
        varchar uf "CHECK ^[A-Z]{2}$"
        varchar bairro
        varchar rua
        varchar numero
        numeric latitude "CHECK -90..90"
        numeric longitude "CHECK -180..180"
        numeric area_m2 "CHECK > 0"
        numeric largura_m "par com comprimento"
        numeric comprimento_m "par com largura"
        geometry geom "POLYGON 31982, CHECK ST_IsValid"
        geometry ponto "POINT 4326, GERADA de lat/lon"
        boolean ativo
        timestamptz criado_em
        timestamptz atualizado_em
    }
```

`ponto` é coluna **gerada** a partir de latitude/longitude: não há como
dessincronizar do par de coordenadas, e ela sustenta o índice GiST usado pelo
mapa (imóveis legados não têm polígono).

## Onde cada requisito do enunciado foi resolvido

| Tarefa | Onde |
|--------|------|
| 1. Separar em duas páginas | rotas `/imoveis` e `/imoveis/novo` |
| 2. Filtros na listagem | `ImovelSpecs` + `ImovelFiltro` (servidor) |
| 3. Página de edição sem novo GET | `ImoveisStore` + `edicao-sem-novo-get.spec.ts` |
| 4. Proprietário como entidade | `V3__proprietario.sql` + `MigracaoProprietarioTest` |
| 5. Renomear refletindo em todos | FK; `ProprietarioService.renomear` |
| 6. Volume | paginação, índices `V4`, `docs/PERFORMANCE.md` |
| 7. Mapa | `MapaController` + `MapaComponent` (OpenLayers) |
| 8. Geometria sem sobreposição | `GeometriaService` + `V5` + `ConcorrenciaGeometriaTest` |
