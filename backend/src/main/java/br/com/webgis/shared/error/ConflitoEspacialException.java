package br.com.webgis.shared.error;

/**
 * O poligono do imovel intersecta o de um imovel ja cadastrado. Traduzida para
 * HTTP 409, carregando o id do imovel conflitante para que a interface consiga
 * apontar exatamente qual lote esta no caminho.
 */
public class ConflitoEspacialException extends RuntimeException {

	private final Long idImovelConflitante;

	public ConflitoEspacialException(Long idImovelConflitante) {
		super("A area informada conflita com o imovel %d ja cadastrado".formatted(idImovelConflitante));
		this.idImovelConflitante = idImovelConflitante;
	}

	public Long getIdImovelConflitante() {
		return idImovelConflitante;
	}
}
