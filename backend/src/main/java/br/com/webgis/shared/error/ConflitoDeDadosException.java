package br.com.webgis.shared.error;

/**
 * Violacao de uma regra de unicidade do dominio (por exemplo, dois proprietarios
 * com o mesmo nome normalizado). Traduzida para HTTP 409.
 */
public class ConflitoDeDadosException extends RuntimeException {

	public ConflitoDeDadosException(String mensagem) {
		super(mensagem);
	}

	public ConflitoDeDadosException(String mensagem, Throwable causa) {
		super(mensagem, causa);
	}
}
