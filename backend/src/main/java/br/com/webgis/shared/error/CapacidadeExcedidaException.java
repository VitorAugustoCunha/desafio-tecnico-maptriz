package br.com.webgis.shared.error;

/**
 * O pool de trabalho GIS esta saturado (fila cheia). Traduzida para HTTP 503 com
 * {@code Retry-After}.
 *
 * <p>E o sinal de backpressure: em vez de deixar a fila crescer sem limite ate
 * derrubar a JVM por falta de memoria, o servidor recusa a tarefa de forma
 * controlada e diz ao cliente quando tentar de novo.
 */
public class CapacidadeExcedidaException extends RuntimeException {

	private final int segundosParaNovaTentativa;

	public CapacidadeExcedidaException(String mensagem, int segundosParaNovaTentativa) {
		super(mensagem);
		this.segundosParaNovaTentativa = segundosParaNovaTentativa;
	}

	public int getSegundosParaNovaTentativa() {
		return segundosParaNovaTentativa;
	}
}
