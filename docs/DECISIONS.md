# Decisões de arquitetura (ADRs)

Registro curto das decisões que mudam a forma da solução. Cada uma tem o
contexto, a escolha, o que foi descartado e o custo assumido.

---

## ADR-001 — Ordem da refatoração: dado antes de funcionalidade

**Contexto.** O código original tinha injeção de SQL, corrupção silenciosa de
dados e nenhuma validação, e ao mesmo tempo faltavam 8 tarefas do enunciado.

**Decisão.** Parar a sangria primeiro: injeção de SQL, perda de dado no `UPDATE`,
erro reportado como sucesso. Só depois contrato de API, modelagem, volume,
frontend e, por último, os diferenciais (mapa e geometria).

**Por quê.** Cada funcionalidade nova construída sobre a base insegura seria
mais código para reescrever depois. E um sistema que grava dado errado
respondendo `200` é pior do que um sistema com menos funcionalidades.

**Custo.** As tarefas visíveis (mapa, geometria) aparecem tarde no histórico do
Git. É proposital: o histórico mostra a ordem de prioridade real.

---

## ADR-002 — Dados legados na trilha de migrations

**Contexto.** O enunciado diz que "a base já tem imóveis cadastrados com o
proprietário em texto" e que "a migração não pode perdê-los". No original, esses
dados vinham de `data.sql` com `spring.sql.init.mode=always`, que **duplicava o
seed a cada subida** (medido: 13 → 25 linhas após um restart).

**Decisão.** Mover as 12 linhas para a migration `V2__dados_legados.sql`, ainda
com o proprietário como texto, e migrá-las na `V3`.

**Por quê.** É o que torna a migração da tarefa 4 reproduzível e testável: o
teste aplica o Flyway até a `V2`, confere o estado, aplica a `V3` e prova que
nada se perdeu. Com o seed fora da trilha, a `V3` rodaria sempre sobre uma
tabela vazia e não provaria nada.

**Descartado.** Seed em location separada ativada por profile — exigiria
`ignore-migration-patterns=*:missing` e deixaria o teste de migração sem o dado
de entrada.

**Custo assumido.** Um deploy em produção realmente nova receberia 12 imóveis de
demonstração. Em um produto de verdade, esse seed sairia da trilha versionada.
Está anotado no cabeçalho da própria migration.

---

## ADR-003 — Paginação por offset com `COUNT`, e não por cursor

**Contexto.** A tarefa 6 pede que a listagem se sustente com volume grande.
Offset degrada com a profundidade; cursor (keyset) não, mas não oferece total
nem navegação por número de página.

**Decisão.** Offset com `COUNT` na listagem da tela; keyset na exportação em
lote.

**Por quê.** São usos diferentes:

- A tela é operada por alguém que precisa saber *quantos* imóveis atendem ao
  filtro e navegar por páginas. Medido: o `COUNT` filtrado custa 27 ms com
  índice, e a página 500 custa 116 ms contra 90 ms da primeira — degradação real
  mas pequena, e alcançável só clicando "próxima" 500 vezes.
- A exportação percorre o cadastro inteiro em lotes, sem UI e sem total. Ali o
  offset ficaria progressivamente mais caro, então usa
  `WHERE id > :ultimoId` (`MapaRepository.loteParaExportacao`).

**Custo assumido.** Se o volume crescer uma ordem de grandeza, a listagem
precisará migrar para keyset e a UI perde a navegação por número de página.
Medições em `docs/PERFORMANCE.md`.

---

## ADR-004 — Projeção 4326 → 31982 feita pelo PostGIS, não em Java

**Contexto.** A tarefa 8 exige polígono em `geometry(POLYGON, 31982)` a partir
de latitude/longitude (WGS 84) e dimensões em metros.

**Decisão.** A construção do retângulo é uma função SQL
(`webgis_retangulo`, migration `V5`) que usa `ST_Transform` do PostGIS/PROJ. A
entidade JPA **não mapeia** as colunas `geom` e `ponto`.

**Por quê.**

1. Quem compara as geometrias (`ST_Intersects`), valida (`ST_IsValid`) e indexa
   (GiST) é o PostGIS. Calcular a projeção em Java com outra biblioteca criaria
   duas implementações da mesma transformação, que podem divergir na última casa
   decimal — e divergência aqui significa aceitar um lote que deveria ser
   recusado.
2. Evita adicionar GeoTools/proj4j só para reprojetar um ponto.
3. Mantendo a geometria fora da entidade, a listagem nunca arrasta polígono por
   engano, e não é preciso Hibernate Spatial.

**Convenção adotada e documentada.** Latitude/longitude são o **centro** do
lote. Metade da largura para cada lado em X, metade do comprimento em cada lado
em Y, já em metros no plano projetado. Há teste conferindo que o centroide do
polígono coincide com o ponto informado.

**Limitação assumida, não contornada em silêncio.** EPSG:31982 é SIRGAS 2000 /
UTM **zona 22S** (meridiano central 51°W). Cobre bem Sul e Sudeste; quanto mais
o imóvel se afasta da zona, maior a distorção de distância e área. O enunciado
fixa esse SRID e ele foi mantido. As alternativas seriam SRID por zona UTM
(correto, porém impede comparar geometrias de zonas diferentes sem reprojetar)
ou um CRS único para o país como EPSG:5880 (comparável em todo o território, com
distorção distribuída). Trocar o SRID pedido sem avisar seria pior que a
limitação.

**Custo.** A geração de geometria só é testável com banco real — o que já é o
caso de toda a suíte (Testcontainers com PostGIS).

---

## ADR-005 — Não sobreposição garantida por advisory lock

**Contexto.** "Verificar se há conflito" e "gravar" são dois passos. Em READ
COMMITTED, duas transações simultâneas não enxergam uma à outra: as duas
consultam, nenhuma encontra conflito, e as duas gravam. O cadastro termina com
dois lotes no mesmo lugar e nenhum erro em lugar nenhum.

**Decisão.** `pg_advisory_xact_lock` com chave constante, tomado antes da
verificação e liberado automaticamente no commit ou rollback.

**Por quê.** Serializa as escritas *com geometria* sem afetar leitura alguma —
nenhuma consulta pega esse lock. Quem chega depois espera, e ao acordar já
enxerga a geometria commitada pelo primeiro, encontrando o conflito.

**Descartado.**

- `EXCLUDE USING gist (geom WITH &&)`: resolveria no banco, mas a mensagem de
  erro não traz o **id do imóvel conflitante**, que a interface usa para
  oferecer "ver o imóvel 7". Além disso, `&&` compara bounding box, não a
  geometria real.
- `SERIALIZABLE`: resolveria, ao custo de erros de serialização em operações que
  não têm nada a ver com geometria.
- Chave de lock por célula de grade: melhora a concorrência, mas exige travar
  todas as células tocadas pelo retângulo em ordem determinista para não criar
  deadlock. Complexidade sem problema medido.

**Custo assumido.** Escritas com geometria são serializadas globalmente. Para
cadastro imobiliário (escrita rara, leitura intensa) é irrelevante. Se virar
gargalo, o próximo passo é a chave por célula.

**Prova.** `ConcorrenciaGeometriaTest` dispara 6 cadastros simultâneos do mesmo
retângulo, sincronizados por `CyclicBarrier`, e exige que exatamente 1 seja
aceito e 5 recebam `409`. Um segundo teste confirma que áreas distintas não se
bloqueiam.

---

## ADR-006 — Pool de trabalho GIS só para o que pode ser assíncrono

**Contexto.** O enunciado pede um pool de trabalho limitado com backpressure.
Existe a tentação de jogar a validação de sobreposição nele.

**Decisão.** A validação de sobreposição e a gravação continuam **síncronas e
transacionais**. O pool (`gisWorkerExecutor`) atende exportação GeoJSON em lote,
implementada de ponta a ponta.

**Por quê.** Consistência espacial é regra crítica: processar de forma eventual
aceitaria, ainda que por instantes, dois lotes no mesmo lugar — e o usuário
receberia "cadastrado com sucesso" para algo que pode ser rejeitado depois. É
exatamente o tipo de mentira que este projeto está corrigindo.

Exportação é o oposto: longa, custosa, e ninguém precisa dela de forma síncrona.
Rodando no pool do Tomcat, algumas exportações simultâneas consumiriam as
threads que atendem o CRUD.

**Backpressure.** Fila limitada + `AbortPolicy` → `503` com `Retry-After`. Fila
ilimitada não é "aceitar tudo": é adiar a falha até faltar memória, perdendo
todas as tarefas de uma vez.

**Detalhes que importam.** Cada lote abre a própria transação de leitura *dentro*
da thread do worker (`TransactionTemplate` com `REQUIRES_NEW`) — nunca herda a
de quem submeteu, e nunca segura uma transação pela exportação inteira. O
contexto de log (MDC) é propagado por `TaskDecorator`, então o `requestId` da
requisição original aparece nas linhas escritas pelo worker.

**Limitação assumida.** O registro das exportações é em memória e os arquivos
ficam em disco local: isto assume **uma instância**. Com várias réplicas, o
estado precisaria ir para banco/Redis e os arquivos para object storage.

**Escala vem primeiro do banco.** Paginação, índices e consulta espacial por
viewport são a primeira linha: eles reduzem o trabalho que existe. O pool apenas
move trabalho inevitável para fora do caminho da requisição. Um pool não conserta
uma consulta que varre 500 mil linhas.

---

## ADR-007 — O que o Web Worker faz, e o que ele deliberadamente não faz

**Contexto.** O enunciado pede um Web Worker real, com propósito, e proíbe
concorrência decorativa.

**Decisão.** O worker cuida do pipeline de preparação das feições já recebidas:
validação e normalização de coordenadas, achatamento em `Float64Array`,
estatísticas e agrupamento por município.

**O que ele não faz, e por quê.**

- **Clustering.** Fica no PostGIS (`GROUP BY ST_SnapToGrid`). Agrupar no
  navegador exigiria baixar todos os pontos primeiro — exatamente o custo que o
  cluster deveria evitar. No banco, um viewport com centenas de milhares de
  imóveis devolve algumas dezenas de feições.
- **Chamadas HTTP.** `fetch` não bloqueia a thread principal; mover isso para um
  worker seria concorrência decorativa.
- **Renderização.** O worker não tem DOM. OpenLayers continua na thread
  principal.

**Como foi construído para ser defensável.** As funções puras vivem **fora** do
worker (`gis-pipeline.ts`) e são testadas direto, sem `postMessage`. O worker é
uma casca fina. Há `requestId` para descartar resposta obsoleta (arrastar o mapa
gera pedidos em sequência), cancelamento lógico entre lotes, erro serializável e
fallback síncrono para SSR e ambiente de teste.

**Transferable.** O `Float64Array` de coordenadas é transferido, não copiado.
Com lotes grandes, a cópia estrutural do `postMessage` custa mais que o próprio
processamento.

**Honestidade sobre o ganho.** Nos volumes que consegui medir, o pipeline roda
em poucos milissegundos e o worker não é necessário. Ele existe para o teto de
2.000 feições com polígonos. Não tenho benchmark comparativo conclusivo e não
inventei um — ver `docs/PERFORMANCE.md`.

---

## ADR-008 — Cache da listagem no cliente, com invalidação explícita

**Contexto.** O requisito 3 proíbe nova requisição ao voltar da edição para a
listagem.

**Decisão.** `ImoveisStore` em memória guarda uma consulta por vez, identificada
por uma chave normalizada (filtros + página + tamanho + ordenação). Depois do
`PUT`, o item é substituído **de forma imutável** com o recurso que o próprio
`PUT` devolveu.

**Política de invalidação.**

| Situação | Comportamento |
|----------|---------------|
| Volta da edição, mesma consulta | Reaproveita — sem requisição |
| Muda filtro, página ou ordenação | Nova consulta (são dados diferentes) |
| Entra pela URL ou atualiza o navegador | Nova consulta (o store nasce vazio) |
| Depois de **editar** | Corrige o item em memória |
| Depois de **criar** ou **excluir** | Invalida e recarrega |
| Passados 5 minutos | Nova consulta |

**Por que criar e excluir invalidam.** As duas mudam quais imóveis caem em qual
página e o total. Qualquer correção local seria uma aproximação que diverge do
servidor — e a tela voltaria a mostrar algo que o banco não confirma.

**Detalhe que faz o requisito funcionar.** A volta preserva os query params. A
chave de cache inclui os filtros; voltar sem eles seria outra consulta e
dispararia um GET novo. Há teste cobrindo esse caso específico.

**Custo assumido.** Se a edição tirar o imóvel do filtro corrente (mudar o
município, por exemplo), a linha **continua visível** com os dados novos até a
próxima consulta real. Preferi isso a fazer a linha sumir no instante em que o
usuário salva: sumir sem explicação parece perda de dado.

---

## ADR-009 — Angular zoneless, estado em signals

**Contexto.** O projeto não tem `zone.js` nas dependências — Angular 22 opera
sem ela. É o que explica os `cdr.detectChanges()` manuais espalhados pelo código
original: sem zone e sem signals, a tela não atualizava sozinha após um
`subscribe`.

**Decisão.** Assumir o modo zoneless explicitamente
(`provideZonelessChangeDetection()`) e colocar **todo** estado de tela em
signals, com `ChangeDetectionStrategy.OnPush` em todos os componentes.

**Consequência.** Não há um `detectChanges()` manual no projeto. Valores
derivados (área total da página, mensagens) são `computed()`, recalculados
quando o dado muda — e não a cada ciclo de detecção, como a `totalArea()`
chamada do template no código original.

---

## ADR-011 — Desenhar o lote no mapa, sem abandonar o formato do enunciado

**Contexto.** A tarefa 8 especifica o lote como retângulo derivado de
centro + largura + comprimento. Mas lote real raramente é um retângulo alinhado
aos eixos — num cadastro multifinalitário, a forma vem da matrícula.

**Decisão.** Suportar **as duas formas**, excludentes entre si:

| Modo | Entrada | Área e ponto |
|------|---------|--------------|
| Dimensões | centro + largura/comprimento | área = largura × comprimento |
| Desenho | polígono GeoJSON traçado no mapa | derivados do polígono pelo PostGIS |

**Por que não substituir.** Trocar o retângulo por desenho livre entregaria algo
mais poderoso, mas deixaria de atender ao que a tarefa 8 pede. Manter os dois
preserva a conformidade e ainda cobre o caso real.

**Por que excludentes.** Aceitar dimensões e desenho juntos obrigaria a eleger um
vencedor em silêncio, e o usuário descobriria qual foi olhando o mapa depois de
salvar. Validado nos dois lados (`isFormaUnica` no backend, `formaUnica()` no
formulário).

**No modo desenho, a geometria é a fonte da verdade.** Área vem do `ST_Area` e o
ponto do imóvel vem do `ST_Centroid`, ambos calculados pelo PostGIS e gravados
por cima do que o cliente enviou. Guardar a área que o cliente mandou deixaria o
imóvel afirmando um tamanho que o próprio polígono desmente — e o mapa mostraria
a segunda versão. Há teste que envia `areaM2: 999999` com um desenho pequeno e
exige que o valor gravado seja o do polígono.

**Validação em camadas, cada uma no lugar certo:**

1. **Bean Validation** (`PoligonoGeoJson`, tipado): tipo `Polygon`, anel fechado,
   mínimo de 4 posições, coordenadas na faixa válida. Rejeita com `400` antes de
   qualquer consulta.
2. **`ST_IsValid`** antes de comparar: auto-interseção (a "gravata", erro clássico
   de quem desenha à mão) vira `400` com mensagem útil.
3. **`ST_Intersects`** com o mesmo advisory lock do retângulo — as duas formas
   disputam o mesmo espaço, então precisam estar na mesma fila. Há teste de
   desenho invadindo retângulo e de retângulo invadindo desenho.

A ordem importa: `ST_Intersects` sobre geometria inválida pode lançar erro de
topologia do GEOS, o que viraria `500` e ainda envenenaria a transação. Por isso
validar e comparar são duas consultas, não uma.

**proj4 no frontend.** Para a pré-visualização do retângulo ser *a mesma
geometria* que será gravada — e não uma ilustração aproximada — o cliente
registra o EPSG:31982 e reproduz o cálculo do servidor: projeta o centro, soma
metade das dimensões em cada eixo, volta para 4326. Custa ~50 kB e elimina a
divergência entre o que se vê e o que se salva.

---

## ADR-010 — Mesma origem em produção; CORS só em desenvolvimento

**Contexto.** O controller original tinha `@CrossOrigin(origins = "*")`,
autorizando qualquer site a chamar a API com o navegador da vítima.

**Decisão.** Em produção, o Nginx serve o frontend e faz proxy de `/api` para o
backend: tudo na mesma origem, e CORS deixa de existir. Em desenvolvimento sem
Docker, o dev-server do Angular faz o mesmo proxy; a lista de origens
autorizadas fica em `WEBGIS_CORS_ORIGINS`, vazia por padrão.

**Por quê.** A configuração mais segura é aquela em que o mecanismo de risco não
está presente. Uma lista de origens é melhor que `*`, mas não precisar de lista
alguma é melhor ainda.
