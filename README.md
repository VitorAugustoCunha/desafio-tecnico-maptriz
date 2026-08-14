# Maptriz — Teste Técnico

Cadastro de imóveis georreferenciados. O sistema já funciona: lista, cadastra,
edita e exclui imóveis.

## Stack

| Camada   | Tecnologia                          |
|----------|-------------------------------------|
| Backend  | Java 21, Spring Boot 3.5.16, Maven  |
| Banco    | PostgreSQL                          |
| Frontend | Angular 22                          |

## Pré-requisitos

- JDK 21
- PostgreSQL rodando em `localhost:5432`
- Node.js 20+

## 1. Banco de dados

Use o comando abaixo ou crie manualmente no banco.

```bash
sudo -u postgres psql -f scripts/setup-db.sql
```


Cria o banco `webgis`. A aplicação conecta como o usuário `postgres` (veja
`backend/src/main/resources/application.properties`) — ajuste ali se o seu
Postgres usar outra senha. As tabelas são criadas na primeira subida do backend.

## 2. Backend

```bash
cd backend
./mvnw spring-boot:run
```

Sobe em `http://localhost:8080`. O schema é criado pelo Hibernate a partir da
entidade `Imovel` (`spring.jpa.hibernate.ddl-auto=update`), e o
`src/main/resources/data.sql` popula 12 imóveis de exemplo.

### Campos do imóvel

| Campo          | Tipo    | Observação                        |
|----------------|---------|-----------------------------------|
| `proprietario` | texto   |                                   |
| `municipio`    | texto   |                                   |
| `uf`           | texto   |                                   |
| `bairro`       | texto   |                                   |
| `rua`          | texto   |                                   |
| `numero`       | texto   | aceita `S/N`, `123-A`             |
| `latitude`     | número  | graus decimais (WGS 84)           |
| `longitude`    | número  | graus decimais (WGS 84)           |
| `areaM2`       | número  | área do terreno em m²             |
| `ativo`        | boolean |                                   |

### Endpoints

| Método   | Rota                | Descrição             |
|----------|---------------------|-----------------------|
| `GET`    | `/api/imoveis`      | Lista todos os imóveis |
| `GET`    | `/api/imoveis/{id}` | Busca por id           |
| `POST`   | `/api/imoveis`      | Cadastra              |
| `PUT`    | `/api/imoveis/{id}` | Atualiza              |
| `DELETE` | `/api/imoveis/{id}` | Exclui                |

## 3. Frontend

```bash
cd frontend
npm install
npm start
```

Abre em `http://localhost:4200` e consome a API em `localhost:8080`.

## O exercício

O código está funcionando, mas **não está bom**.

### Parte 1 — revisão

1. Leia o backend e o frontend e **liste os problemas que você encontrar** —
   segurança, performance, arquitetura, manutenibilidade, boas práticas do
   Spring e do Angular.
2. Classifique cada problema por gravidade e explique **por que** é um problema.
3. **Refatore** o que você considerar mais crítico. Não é necessário corrigir
   tudo — é mais importante justificar as escolhas e a ordem de prioridade.

Use o sistema antes de ler o código. Cadastre alguns imóveis, edite, exclua.
O que acontece na tela nem sempre é o que aconteceu no banco.

### Parte 2 — tarefas

Hoje o sistema é uma tela só, com o formulário e a listagem juntos. As tarefas
abaixo evoluem isso. A ordem é sugerida, não obrigatória — se você preferir
outra, explique o porquê.

**Não é obrigatório entregar todos os exercícios mas explicar bem os que fez.**

**1. Separar em duas páginas**

Hoje o cadastro e a listagem dividem a mesma tela. Separe em duas páginas: uma
para **criar** o imóvel e outra para a **listagem**.

**2. Filtros na listagem**

A listagem precisa de filtro por **proprietário** e por **município**.

**3. Página de edição**

Crie uma terceira página, dedicada a editar o imóvel.

> **Requisito:** ao voltar da edição para a listagem, **não pode haver uma nova
> requisição**. A listagem deve reaproveitar os dados que já estavam em memória.

**4. Página de proprietários**

Hoje o proprietário é apenas um campo de texto dentro do imóvel. Modele o
proprietário como **entidade própria**, com **relacionamento** com o imóvel.

Com isso feito, crie uma página que lista os proprietários. Ao clicar em um
deles, mostrar os imóveis dos quais ele é dono.

> **Atenção aos dados existentes:** a base já tem imóveis cadastrados com o
> proprietário em texto. A migração não pode perdê-los.

**5. Renomear proprietário**

Deve ser possível **alterar o nome de um proprietário**.

> **Requisito:** se esse proprietário for dono de mais de um imóvel, a alteração
> precisa valer para **todos** os imóveis dele.

**6. Preparar a listagem para grande volume**

O seed local tem 12 imóveis, mas o cadastro real vai cobrir mais de mil
municípios — e muito mais imóveis do que isso. A listagem de hoje carrega e
renderiza tudo de uma vez, o que nesse cenário quebra dos dois lados: fica
**lenta** e vira uma tabela **impossível de usar**.

Faça as alterações necessárias para que a listagem se sustente com um grande
volume de dados, tanto no servidor quanto na interface. Diga o que você mediu ou
o que assumiu para chegar nas suas escolhas.

**7. Mapa (desejável mas não obrigatório)**

Crie uma tela com um mapa que permita visualizar os imóveis cadastrados.

Cada imóvel deve ser representado no mapa utilizando sua **latitude e longitude**.

Você pode utilizar a biblioteca de mapas que preferir. **OpenStreetMap**, **OpenLayers**, **Leaflet** ou outra solução equivalente são permitidas.

Não é necessário implementar funcionalidades avançadas de GIS. O objetivo é demonstrar que você consegue integrar uma biblioteca de mapas à aplicação, consumir os dados da API e representar informações geográficas na interface.

**Requisitos mínimos:**

- Exibir um mapa.
- Exibir os imóveis cadastrados como pontos no mapa.

**8. Desafio Opcional — imóveis georreferenciados sem sobreposição (nivel sênior)**

Hoje o imóvel guarda um ponto (`latitude`/`longitude`) e uma área solta em m².
A ideia aqui é passar a representar a **área real** do imóvel e garantir que dois
imóveis não ocupem o mesmo espaço.

No cadastro, o sistema recebe a posição geográfica (**latitude** e **longitude**)
e as dimensões (**largura** e **comprimento**). A partir disso, monte uma
geometria `POLYGON` com a área do imóvel e persista no banco.

Neste projeto as geometrias são armazenadas com **SRID 31982**:

```sql
geom public.geometry(POLYGON, 31982) NULL
```

Antes de inserir, verifique se o polígono gerado **intersecta ou sobrepõe** algum
imóvel já cadastrado. Se houver conflito, o cadastro é rejeitado e o usuário
recebe uma mensagem dizendo que a área selecionada conflita com outro imóvel.

Requisitos mínimos:

- Receber latitude, longitude, largura e comprimento.
- Gerar o polígono a partir desses dados.
- Persistir a geometria no banco.
- Impedir o cadastro quando houver sobreposição.
- Exibir no mapa os imóveis cadastrados.

Você tem liberdade total na abordagem: conversão das coordenadas, criação do
polígono, validação da geometria, consulta espacial e comunicação entre frontend
e backend. **PostGIS não é obrigatório** (extensão do postgres),  mas usá-lo conta como diferencial.

---

Não há uma única resposta certa. Queremos entender como você lê código que já
existe, como decide o que mexer primeiro, e como escreve código novo dentro de
uma base que você não escreveu. Explicar uma decisão vale mais do que entregar
todas as tarefas.

--- 

## Avaliação

Após concluir os projetos, tenha certeza de detalhar as tecnologias utilizadas em seus respectivos READMEs. Após isso, envie os links para os projetos no GitHub para o e-mail processoseletivo@maptriz.com.br.

A equipe técnica da Maptriz realizará um *Code Review* de seus projetos e, eventualmente, marcará uma reunião remota para discutir a sua solução dos desafios.

## Conclusão

Boa sorte no desafio! A equipe Maptriz deseja muito sucesso para você!
