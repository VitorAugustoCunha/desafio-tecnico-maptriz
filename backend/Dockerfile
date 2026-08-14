# syntax=docker/dockerfile:1

# ---------- build ----------
# Versao fixa (nao `latest`) para o build ser reproduzivel.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# O pom entra sozinho primeiro: enquanto ele nao mudar, o Docker reaproveita a
# camada de dependencias e nao rebaixa a internet a cada alteracao de codigo.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

COPY src ./src

# Os testes usam Testcontainers e precisam de um Docker acessivel, que nao
# existe dentro do build da imagem. A suite roda no CI e localmente, em etapa
# propria (ver .github/workflows/ci.yml).
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl para o healthcheck; sem ele nao ha como o compose saber se subiu.
RUN apk add --no-cache curl

# Usuario sem privilegio: um processo comprometido nao vira root no container.
RUN addgroup -S webgis && adduser -S -G webgis webgis

WORKDIR /app

COPY --from=build --chown=webgis:webgis /build/target/*.jar app.jar

USER webgis

EXPOSE 8080

# Readiness do Actuator: responde OK so depois do Flyway e do pool de conexao.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# MaxRAMPercentage em vez de -Xmx fixo: a JVM respeita o limite de memoria do
# container, qualquer que seja ele.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
