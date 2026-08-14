package br.com.webgis.shared.error;

/**
 * Recurso inexistente. Traduzida para HTTP 404 pelo {@link ApiExceptionHandler}.
 *
 * <p>No codigo original, buscar um id inexistente devolvia {@code 200} com corpo
 * vazio e excluir um id inexistente devolvia {@code 200 {"status":"ok"}} — o
 * cliente nao tinha como distinguir "nao existe" de "deu certo".
 */
public class RecursoNaoEncontradoException extends RuntimeException {

	private final String recurso;
	private final Object identificador;

	public RecursoNaoEncontradoException(String recurso, Object identificador) {
		super("%s %s nao encontrado".formatted(recurso, identificador));
		this.recurso = recurso;
		this.identificador = identificador;
	}

	public String getRecurso() {
		return recurso;
	}

	public Object getIdentificador() {
		return identificador;
	}
}
