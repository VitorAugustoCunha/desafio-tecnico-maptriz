# Code Review — auditoria do código original

Auditoria feita **antes** de qualquer refatoração, com a aplicação original rodando
(commit `685588d`, tag de referência do baseline).

## Como as evidências foram coletadas

O ambiente de desenvolvimento tinha apenas JDK 17, e o projeto exige Java 21. O
baseline foi executado em container:

```bash
docker run -d --name baseline-pg --network webgis-baseline \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=webgis -p 55432:5432 postgres:16-alpine

docker run -d --name baseline-api --network webgis-baseline \
  -v "F:\desafio-tecnico-main\backend:/app" -w /app -p 58080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://baseline-pg:5432/webgis \
  maven:3.9-eclipse-temurin-21 mvn -q spring-boot:run
```

Cada linha da tabela referencia uma evidência (`A`–`T`) reproduzida contra essa
instância e conferida direto no Postgres com `psql`. As saídas brutas estão em
[Anexo — saídas reais](#anexo--saídas-reais).

## Resumo por gravidade

| Gravidade | Qtd | Natureza |
|-----------|-----|----------|
| Crítica   | 6   | Injeção de SQL, perda silenciosa de dados, erro reportado como sucesso |
| Alta      | 19  | Contrato de API, validação, arquitetura, tipagem, performance |
| Média     | 17  | Observabilidade, semântica HTTP, UX, acessibilidade |
| Baixa     | 2   | Higiene de código e dependências |

## Achados

### Segurança

| ID | Camada | Problema | Evidência | Impacto | Gravidade | Correção escolhida | Status |
|----|--------|----------|-----------|---------|-----------|--------------------|--------|
| SEC-01 | Backend / persistência | `id` concatenado direto na SQL nativa em `buscarPorId`, `atualizar` e `excluir` (`ImovelService.java:41,95,110`) | `GET /api/imoveis/0%20or%201%3D1` → HTTP 200 devolvendo o imóvel `id=1`; `GET /api/imoveis/(select id from imovel order by id desc limit 1)` → devolve `id=12` | Leitura, alteração e remoção arbitrária de dados por qualquer cliente da API. Um `DELETE` com predicado sempre-verdadeiro apaga a base inteira | **Crítica** | Spring Data JPA + JPQL/queries parametrizadas; `id` tipado como `Long` na assinatura | ⏳ Planejado |
| SEC-02 | Backend / persistência | Valores de texto concatenados no `INSERT`/`UPDATE` (`ImovelService.java:55-65,83-95`) | `POST` com `"proprietario":"Vitor O'Brien"` → HTTP 500: o apóstrofo fecha a string SQL | Mesma superfície de injeção pelo corpo da requisição; além disso qualquer nome com apóstrofo é irregistrável | **Crítica** | Persistência via repositório JPA, sem concatenação | ⏳ Planejado |
| SEC-03 | Backend / web | `@CrossOrigin(origins = "*")` no controller (`ImovelController.java:16`) | `GET` com `Origin: https://site-malicioso.example` → `Access-Control-Allow-Origin: *` | Qualquer site pode chamar a API pelo navegador da vítima. Combinado com SEC-01, vira exclusão remota de dados | **Alta** | CORS por configuração (lista de origens em variável de ambiente) e, em produção, mesma origem via proxy reverso do Nginx | ⏳ Planejado |
| SEC-04 | Backend / config | Usuário e senha do banco versionados (`application.properties:5-6`) | Arquivo em texto claro no Git | Credencial em repositório; rotação exige commit | **Alta** | Configuração externalizada por variáveis de ambiente, `.env.example` sem segredo real | ⏳ Planejado |
| SEC-05 | Backend / observabilidade | `management.endpoint.health.show-details=always` sem autenticação | `GET /actuator/health` → expõe caminho do disco, espaço livre e tipo do banco | Reconhecimento de infraestrutura por usuário anônimo | **Média** | `show-details=when-authorized`, exposição mínima (`health`), sem detalhes públicos | ⏳ Planejado |
| SEC-06 | Backend / observabilidade | `show-sql=true` e `System.out.println("SQL: " + sql)` (`ImovelService.java:27,67,97`) | Log do container imprime a SQL completa com os valores | Dados pessoais (nome, endereço, coordenadas) em log; ruído que inviabiliza log estruturado | **Média** | SLF4J com log estruturado e sem dados sensíveis; `show-sql` desligado | ⏳ Planejado |
| SEC-07 | Backend / dependência | `spring-boot-devtools` ativo | Log do baseline: `LiveReload server is running on port 35729` | Porta extra aberta e reinício automático; não deve existir em imagem de produção | **Média** | Devtools removido do build de produção | ⏳ Planejado |

### Consistência de dados

| ID | Camada | Problema | Evidência | Impacto | Gravidade | Correção escolhida | Status |
|----|--------|----------|-----------|---------|-----------|--------------------|--------|
| DAT-01 | Backend / service | `UPDATE` monta `latitude = null` quando o campo vem nulo, sobrescrevendo o valor existente | Antes: `13\|Teste Alterado\|-25.4\|-49.2\|100.00`. `PUT` com `latitude/longitude/areaM2` nulos → **HTTP 200 `{"status":"ok"}`**. Depois: `13\|Teste Alterado\|\|\|` | **Este é o "a tela mente" do enunciado.** O usuário limpa um campo numérico, a tela diz "Imóvel atualizado!" e as coordenadas do imóvel somem do banco | **Crítica** | DTO de atualização com Bean Validation: campos obrigatórios não aceitam nulo, o request é rejeitado com 400 antes de tocar no banco | ⏳ Planejado |
| DAT-02 | Backend / service | Campo de texto ausente no JSON vira a string literal `"null"` (concatenação de `null` em Java) | `PUT` sem `bairro` → HTTP 200; no banco `bairro = 'null'` com `length = 4` | Corrupção silenciosa: o dado parece preenchido, filtros e relatórios passam a contar `"null"` como bairro | **Alta** | DTO tipado + validação de obrigatoriedade; sem concatenação | ⏳ Planejado |
| DAT-03 | Backend / service | `catch (Exception e) { e.printStackTrace(); return null; }` (`ImovelService.java:73-76,103-106`) | `POST` com corpo `[1,2,3]` → **HTTP 200 com corpo vazio** (o `ClassCastException` é engolido) | Falha reportada como sucesso. O frontend só tem o callback de sucesso, então mostra "Imóvel cadastrado!" para uma operação que não aconteceu | **Crítica** | Exceções propagadas e traduzidas por `@RestControllerAdvice` em `ProblemDetail` (RFC 9457) com o status correto | ⏳ Planejado |
| DAT-04 | Backend / config | `data.sql` + `spring.sql.init.mode=always` reexecuta o seed a cada subida | Antes do restart: 13 imóveis. Depois: **25**. `Maria Aparecida Souza` aparece 2x | Base de demonstração duplica indefinidamente; qualquer contagem ou teste vira ruído | **Alta** | Seed movido para migration Flyway idempotente (`INSERT ... WHERE NOT EXISTS`), `spring.sql.init` desligado | ⏳ Planejado |
| DAT-05 | Backend / config | `spring.jpa.hibernate.ddl-auto=update` | Log: `Hibernate: create table imovel (...)` na primeira subida | Schema derivado da entidade, sem histórico, sem revisão e sem caminho de rollback. `update` nunca remove nem altera coluna, então o schema real diverge silenciosamente | **Alta** | Flyway com migrations versionadas; `ddl-auto=validate` | ⏳ Planejado |
| DAT-06 | Backend / API | Nenhuma validação de entrada, apesar de `spring-boot-starter-validation` estar no `pom.xml` | `POST` com `uf: "XXXXX"`, `latitude: 999`, `areaM2: -50` → HTTP 500 (estouro do `varchar(2)`), não 400 | Regra de domínio delegada ao banco; mensagem de erro inútil; latitude 999 seria aceita se a coluna permitisse | **Alta** | Bean Validation nos DTOs com faixas geográficas, UF normalizada, textos obrigatórios e limitados | ⏳ Planejado |
| DAT-07 | Modelagem | `proprietario` é texto livre repetido em cada imóvel | `data.sql` repete o nome por linha | Sem identidade: renomear exige `UPDATE` em massa, erro de digitação cria "outro" proprietário, não há como listar proprietários | **Alta** | Entidade `proprietario` + FK, com migração de dados sem perda (tarefa 4) | ⏳ Planejado |

### Contrato da API

| ID | Camada | Problema | Evidência | Impacto | Gravidade | Correção escolhida | Status |
|----|--------|----------|-----------|---------|-----------|--------------------|--------|
| API-01 | Backend / controller | `GET /{id}` inexistente devolve `200` com corpo vazio (`return null`) | `GET /api/imoveis/999999` → HTTP 200, 0 bytes | Cliente não distingue "não existe" de "existe e é vazio"; quebra cache e tratamento de erro | **Alta** | `404` com `ProblemDetail` via exceção de domínio | ⏳ Planejado |
| API-02 | Backend / controller | `DELETE` de id inexistente devolve `200 {"status":"ok"}` | `DELETE /api/imoveis/999999` → HTTP 200 `{"status":"ok"}` | Confirma exclusão que não ocorreu | **Alta** | `404` quando não existe, `204 No Content` quando exclui | ⏳ Planejado |
| API-03 | Backend / controller | `POST` devolve `200 {"status":"ok"}`, sem `Location` e sem o recurso criado | `POST` válido → `{"status":"ok"}`; o id só foi descoberto consultando o banco | Cliente não sabe o id do que acabou de criar — por isso o frontend recarrega a lista inteira | **Média** | `201 Created` com header `Location` e o recurso no corpo | ⏳ Planejado |
| API-04 | Backend / web | Nenhum `@RestControllerAdvice` | Erros viram 500 genérico do Spring ou 200 vazio | Sem contrato de erro; cliente não tem como reagir | **Alta** | `@RestControllerAdvice` com `ProblemDetail` (RFC 9457) para 400/404/409/503 | ⏳ Planejado |
| API-05 | Backend / controller | Assinaturas `Object listar()`, `Object criar(@RequestBody Object corpo)` | `ImovelController.java:23-46` | Não há contrato: nem o compilador, nem a IDE, nem a documentação sabem o que entra e o que sai. É o que permite DAT-02 e DAT-03 | **Alta** | `record`s de request/response e `ResponseEntity<T>` tipado | ⏳ Planejado |
| API-06 | Backend / controller | `@PathVariable String id` | `ImovelController.java:29,39,44` | Tipo errado é o que viabiliza SEC-01; `Long` faria o Spring rejeitar `0 or 1=1` com 400 | **Média** | `@PathVariable Long id` | ⏳ Planejado |

### Arquitetura e manutenibilidade (backend)

| ID | Camada | Problema | Evidência | Impacto | Gravidade | Correção escolhida | Status |
|----|--------|----------|-----------|---------|-----------|--------------------|--------|
| ARC-01 | Backend | Nenhum repositório; `EntityManager` com SQL nativa dentro do service | `ImovelService.java:19` | Reescreve à mão o que o Spring Data entrega pronto, e é a origem de SEC-01/SEC-02 | **Alta** | `ImovelRepository extends JpaRepository` + `Specification` para filtros | ⏳ Planejado |
| ARC-02 | Backend / entidade | Campos `public` sem encapsulamento | `Imovel.java:19-54` | Qualquer código altera o estado sem passar por regra de negócio | **Média** | Campos privados com construtores e métodos de mudança de estado | ⏳ Planejado |
| ARC-03 | Backend | `HashMap` cru como objeto de transporte (`montarImovel`) | `ImovelService.java:117-133` | Sem tipo, sem contrato, sem refactor seguro; a entidade JPA fica sem uso real | **Alta** | DTOs (`record`) de entrada e saída + mapper explícito | ⏳ Planejado |
| ARC-04 | Backend | `@Transactional` na classe inteira, incluindo leituras | `ImovelService.java:15` | Transação de escrita aberta em toda consulta; segura conexão sem necessidade | **Média** | `@Transactional(readOnly = true)` na classe, escrita só onde altera | ⏳ Planejado |
| ARC-05 | Backend | Injeção por campo com `@Autowired` | `ImovelController.java:19`, `ImovelService.java:18` | Impede `final`, dificulta teste unitário e esconde dependências | **Baixa** | Injeção por construtor | ⏳ Planejado |
| ARC-06 | Backend | `System.out.println` como log | `ImovelController.java:24`, `ImovelService.java:27` | Sem nível, sem contexto, sem correlação; não dá para filtrar em produção | **Média** | SLF4J com log estruturado | ⏳ Planejado |
| ARC-07 | Backend / config | `spring.jpa.open-in-view=true` | `application.properties:11` | Mantém a sessão JPA aberta durante a renderização da resposta; esconde N+1 e prende conexão do pool | **Média** | `open-in-view=false` | ⏳ Planejado |
| ARC-08 | Backend | Raw types (`List`, `Map`, `HashMap`, `ArrayList`) | `ImovelService.java:30-32,53,70` | Perde checagem do compilador; `(Map) corpo` estoura em runtime (ver DAT-03) | **Média** | Tipos genéricos em todo o código | ⏳ Planejado |
| ARC-09 | Backend | Nenhum teste | Não existe `src/test` | Nenhuma rede de segurança para refatorar | **Alta** | JUnit 5 + MockMvc + Testcontainers PostGIS | ⏳ Planejado |
| ARC-10 | Backend / build | Lombok declarado e não usado | `pom.xml:53-57` | Processador de anotações e dependência sem função | **Baixa** | Removido (o projeto usa `record`s) | ⏳ Planejado |

### Performance

| ID | Camada | Problema | Evidência | Impacto | Gravidade | Correção escolhida | Status |
|----|--------|----------|-----------|---------|-----------|--------------------|--------|
| PERF-01 | Backend + Frontend | `select ... from imovel order by proprietario` sem `LIMIT`, e o frontend renderiza tudo | `ImovelService.java:25`; `imoveis.html:56` | No cenário do enunciado (mais de mil municípios) a resposta cresce sem limite: memória do servidor, tráfego e DOM. É o item 6 do desafio | **Crítica** | Paginação e filtros no servidor, projeção de listagem, `size` máximo configurável | ⏳ Planejado |
| PERF-02 | Banco | Nenhum índice além da PK | Schema gerado pelo Hibernate | Todo filtro e toda ordenação viram *seq scan* + *sort* | **Alta** | Índices para FK, ordenação e filtros; GIN/`pg_trgm` para busca parcial; GiST para geometria | ⏳ Planejado |
| PERF-03 | Backend | `order by proprietario` ordena a tabela inteira a cada requisição | `ImovelService.java:25` | Sort completo em disco conforme a base cresce | **Média** | Ordenação por whitelist, apoiada em índice, dentro da paginação | ⏳ Planejado |

### Frontend

| ID | Camada | Problema | Evidência | Impacto | Gravidade | Correção escolhida | Status |
|----|--------|----------|-----------|---------|-----------|--------------------|--------|
| FE-01 | Angular / estado | `editar(i)` faz `this.form = i` — atribui a **referência** do item da lista | `imoveis.ts:73-77` | **Segundo "a tela mente".** Digitar no formulário altera a linha da tabela na hora, antes de salvar. Clicar em *Cancelar* deixa a tabela mostrando um valor que nunca foi para o banco | **Crítica** | Formulário reativo tipado alimentado por cópia imutável; a lista só muda quando o servidor confirma | ⏳ Planejado |
| FE-02 | Angular / HTTP | `subscribe()` sem callback de erro em todas as chamadas | `imoveis.ts:41,51,61,84` | Combinado com DAT-03, uma falha vira "Imóvel cadastrado!" na tela. Erro de rede não produz nenhum sinal | **Crítica** | Tratamento de erro por operação, estado de erro na UI e interceptor central | ⏳ Planejado |
| FE-03 | TypeScript | `any` em todo o componente, inclusive no modelo | `imoveis.ts:14-30` | Anula o TypeScript; `i.propietario` (com erro de digitação) compilaria | **Alta** | Interfaces de domínio e `strict` ligado | ⏳ Planejado |
| FE-04 | Angular / config | URL `http://localhost:8080` repetida em 6 pontos | `imoveis.ts:41,51,54,61,64,84,87` | Não sobe para nenhum ambiente; muda em 6 lugares | **Alta** | `environment` + proxy `/api` no dev-server e no Nginx | ⏳ Planejado |
| FE-05 | Angular / arquitetura | `HttpClient` chamado direto do componente | `imoveis.ts:32` | Sem camada de dados: impossível testar, cachear ou reaproveitar | **Alta** | `ImovelApiService` / `ProprietarioApiService` + store | ⏳ Planejado |
| FE-06 | Angular / RxJS | `subscribe` aninhado (PUT → GET dentro do callback) | `imoveis.ts:51-59,61-69,84-91` | *Callback hell*, sem cancelamento, sem tratamento de erro, condição de corrida entre respostas | **Média** | Operadores de composição (`switchMap`) e store; sem aninhamento | ⏳ Planejado |
| FE-07 | Angular / formulário | Nenhuma validação; `[(ngModel)]` sem `name` nem `form` | `imoveis.html:5-26` | Envia latitude 999 e campos vazios para o servidor | **Alta** | Reactive Forms tipados, validação espelhando o backend, mensagem por campo | ⏳ Planejado |
| FE-08 | Angular / render | `*ngFor` sem `track` | `imoveis.html:56` | Angular recria as linhas a cada atualização da lista | **Média** | `@for (... ; track item.id)` | ⏳ Planejado |
| FE-09 | Angular / render | `totalArea()` chamada no template, com laço sobre a lista | `imoveis.html:38`, `imoveis.ts:114-122` | Executa a cada ciclo de detecção de mudanças, não a cada mudança de dado | **Média** | Valor derivado memorizado com `computed()` | ⏳ Planejado |
| FE-10 | Angular / render | `cdr.detectChanges()` manual após cada resposta | `imoveis.ts:45,57,66,89` | Sintoma de detecção de mudanças mal resolvida; mascara o problema real em vez de corrigi-lo | **Média** | `OnPush` + signals; sem `detectChanges` manual | ⏳ Planejado |
| FE-11 | Angular / UX | `confirm()` nativo e `console.log` como feedback | `imoveis.ts:44,80` | Bloqueia a thread, não é estilizável nem acessível, e log não é feedback de usuário | **Média** | Diálogo de confirmação acessível e mensagens na própria interface | ⏳ Planejado |
| FE-12 | Angular / rotas | As três rotas (`''`, `imoveis`, `**`) apontam para o mesmo componente | `app.routes.ts:4-8` | Não há navegação real; `**` esconde 404 e impede deep-link | **Alta** | Rotas dedicadas com lazy loading e rota 404 própria | ⏳ Planejado |
| FE-13 | TypeScript / build | `tsconfig.json` sem `strict` | `tsconfig.json:5-16` | Sem `strictNullChecks`, o `null` de DAT-01/DAT-03 passa despercebido | **Alta** | `strict`, `noUnusedLocals`, `noImplicitAny` e afins ligados | ⏳ Planejado |
| FE-14 | Acessibilidade | Nenhum `<label>`; `placeholder` usado como rótulo; sem foco visível | `imoveis.html:5-26` | Leitor de tela não anuncia o campo; o rótulo some ao digitar | **Média** | `<label for>` real, `aria-describedby` nos erros, foco visível, contraste conferido | ⏳ Planejado |
| FE-15 | Angular / UX | `carregando` só cobre a carga inicial; não há estado vazio nem de erro | `imoveis.html:35` | Tabela vazia é indistinguível de erro de rede | **Média** | Estados explícitos de carregando / vazio / erro | ⏳ Planejado |
| FE-16 | Frontend | Nenhum teste | Não existe nenhum `.spec.ts` | Sem rede de segurança | **Alta** | Vitest + TestBed, incluindo o teste do requisito de não refazer o GET | ⏳ Planejado |
| FE-17 | Angular / dados | Recarrega a lista inteira depois de cada create/update/delete | `imoveis.ts:54,64,87` | Exatamente o que o requisito 3 proíbe, e desperdiça banda a cada operação | **Média** | `ImoveisStore` em memória com atualização imutável do item e política de invalidação documentada | ⏳ Planejado |

## Plano priorizado (registrado antes da refatoração)

A ordem segue **risco de dano ao dado** primeiro, depois **contrato**, depois
**evolução funcional**, e só então diferenciais.

| Ordem | Bloco | Itens | Por quê primeiro |
|-------|-------|-------|------------------|
| 1 | Parar a sangria | SEC-01, SEC-02, DAT-01, DAT-02, DAT-03 | São os itens que **corrompem ou vazam dado em produção**. Enquanto existirem, qualquer funcionalidade nova é construída sobre base instável |
| 2 | Contrato e schema | API-01→06, DAT-04, DAT-05, DAT-06, ARC-01→08 | Sem DTO, sem migration e sem tratamento de erro não dá para evoluir com segurança. Flyway antes de tudo que mexe em schema |
| 3 | Modelagem de proprietário | DAT-07 | Tarefa 4/5 do desafio, e pré-requisito da listagem (filtro por proprietário usa a FK) |
| 4 | Volume | PERF-01, PERF-02, PERF-03, FE-17 | Tarefa 6. Depende de 2 e 3 (índices sobre a FK e sobre os campos filtrados) |
| 5 | Frontend | FE-01→FE-16 | Tarefas 1, 2 e 3. Depende do contrato estável do bloco 2 |
| 6 | Mapa | — | Tarefa 7, desejável |
| 7 | Geometria e não sobreposição | — | Tarefa 8, opcional. Feita por último, sem tocar no que já estava fechado |
| 8 | Testes, Docker, CI, documentação | ARC-09, FE-16 | Transversal: cada bloco entrega seus testes junto, e o empacotamento fecha |

Decisão consciente: **não** fiz reformatação geral do código nem troquei bibliotecas
sem necessidade. Toda dependência nova (Flyway, PostGIS/JTS, OpenLayers,
Testcontainers) entrou para atender um requisito explícito do enunciado.

## Anexo — saídas reais

Sessão de evidências (resumida; textos longos truncados):

```text
A. total de imoveis: 12

B. GET /api/imoveis/0%20or%201%3D1
   HTTP 200 | {"id":1,"proprietario":"Maria Aparecida Souza",...}

C. GET /api/imoveis/(select id from imovel order by id desc limit 1)
   HTTP 200 | {"id":12,"proprietario":"Eduardo Pacheco Silva",...}

D. POST {"proprietario":"Vitor O'Brien",...}
   HTTP 500 | total apos POST: 12

E. POST valido
   HTTP 200 | {"status":"ok"}      <- sem Location, sem id
   id descoberto via psql: 13

F. PUT sem o campo "ativo"
   HTTP 500 | nome no banco apos PUT: Teste Baseline (inalterado)

H. POST {"uf":"XXXXX","latitude":999,"areaM2":-50,...}
   HTTP 500 (estouro de varchar(2), nao validacao)

I. DELETE /api/imoveis/999999   -> HTTP 200 | {"status":"ok"}
J. GET    /api/imoveis/999999   -> HTTP 200 | 0 bytes

N. PUT com latitude/longitude/areaM2 = null
   antes  -> 13|Teste Alterado|-25.4000000|-49.2000000|100.00
   HTTP 200 | {"status":"ok"}
   depois -> 13|Teste Alterado|||

O. PUT sem o campo "bairro"
   HTTP 200 | {"status":"ok"}
   bairro no banco -> [null]   (length = 4, string literal)

Q. GET com Origin: https://site-malicioso.example
   HTTP 200 | Access-Control-Allow-Origin: [*]

R. GET /actuator/health
   HTTP 200 | {"status":"UP","components":{"db":{...,"details":{"database":"PostgreSQL"}},
              "diskSpace":{...,"details":{"total":999075602432,"free":743146688512,
              "path":"/app/."}}}}

S. POST com corpo [1,2,3]
   HTTP 200 | corpo vazio        <- ClassCastException engolido

T. duplicacao do seed
   antes do restart:  13
   depois do restart: 25
   'Maria Aparecida Souza' no banco: 2 linhas
```

### Observação honesta sobre duas hipóteses iniciais

Eu esperava que **todo** erro do service virasse `200` com corpo vazio, já que o
`catch` devolve `null`. Na prática, quando a exceção acontece **dentro** de um
método `@Transactional`, o Spring marca a transação como *rollback-only* e o
commit falha com `UnexpectedRollbackException`, o que devolve `500` (evidências D,
F, H). O `200` mentiroso aparece nos casos em que **nenhuma exceção chega ao
banco**: `ClassCastException` antes do SQL (evidência S) e SQL sintaticamente
válido que grava lixo ou apaga dado (evidências N e O).

Ou seja: o comportamento é pior do que "erro escondido". O sistema **grava dado
errado e responde sucesso** — e é isso que o enunciado quer que se perceba.
