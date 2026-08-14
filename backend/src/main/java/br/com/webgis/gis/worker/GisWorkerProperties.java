package br.com.webgis.gis.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do pool de trabalho GIS.
 *
 * <p>Tudo por ambiente: o dimensionamento certo depende de CPU disponivel e do
 * tamanho do pool de conexoes do banco, e nao pode estar cravado no codigo.
 *
 * @param tamanhoNucleo             threads sempre vivas
 * @param tamanhoMaximo             teto de threads
 * @param capacidadeFila            tarefas aguardando; acima disso a submissao e recusada com 503
 * @param segundosParaNovaTentativa valor do header {@code Retry-After} na recusa
 * @param timeoutTarefaSegundos     tempo maximo de uma exportacao antes de ser abortada
 * @param tamanhoLote               feicoes lidas por vez (chunk) durante a exportacao
 */
@ConfigurationProperties(prefix = "webgis.gis-worker")
public record GisWorkerProperties(
		Integer tamanhoNucleo,
		Integer tamanhoMaximo,
		Integer capacidadeFila,
		Integer segundosParaNovaTentativa,
		Integer timeoutTarefaSegundos,
		Integer tamanhoLote) {

	public GisWorkerProperties {
		tamanhoNucleo = tamanhoNucleo == null ? 2 : tamanhoNucleo;
		tamanhoMaximo = tamanhoMaximo == null ? 4 : tamanhoMaximo;
		capacidadeFila = capacidadeFila == null ? 8 : capacidadeFila;
		segundosParaNovaTentativa = segundosParaNovaTentativa == null ? 30 : segundosParaNovaTentativa;
		timeoutTarefaSegundos = timeoutTarefaSegundos == null ? 120 : timeoutTarefaSegundos;
		tamanhoLote = tamanhoLote == null ? 500 : tamanhoLote;

		if (tamanhoMaximo < tamanhoNucleo) {
			throw new IllegalArgumentException(
					"webgis.gis-worker.tamanho-maximo (%d) nao pode ser menor que tamanho-nucleo (%d)"
							.formatted(tamanhoMaximo, tamanhoNucleo));
		}
		if (capacidadeFila < 1) {
			throw new IllegalArgumentException(
					"webgis.gis-worker.capacidade-fila precisa ser >= 1; fila ilimitada anula o backpressure");
		}
	}
}
