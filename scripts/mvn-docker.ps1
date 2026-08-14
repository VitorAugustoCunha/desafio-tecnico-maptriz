# Executa o Maven dentro de um container com JDK 21.
#
# Motivo: a maquina de desenvolvimento usada tinha apenas JDK 17 instalado, e o
# projeto exige Java 21. O socket do Docker e montado para que o Testcontainers
# consiga subir o PostGIS como container irmao (sibling), e
# TESTCONTAINERS_HOST_OVERRIDE aponta para host.docker.internal porque as portas
# dos containers de teste sao publicadas no host, nao dentro deste container.
#
# Uso:  ./scripts/mvn-docker.ps1 test
#       ./scripts/mvn-docker.ps1 -DskipTests package

param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $MavenArgs)

$repoRoot = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $repoRoot 'backend'

docker run --rm `
    -v "${backend}:/app" `
    -v webgis-m2:/root/.m2 `
    -v //var/run/docker.sock:/var/run/docker.sock `
    -w /app `
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
    -e TESTCONTAINERS_RYUK_DISABLED=true `
    --add-host host.docker.internal:host-gateway `
    maven:3.9-eclipse-temurin-21 mvn @MavenArgs

exit $LASTEXITCODE
