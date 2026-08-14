package br.com.webgis.shared.error;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;

/**
 * Contrato unico de erro da API, em {@link ProblemDetail} (RFC 9457).
 *
 * <p>Substitui o {@code catch (Exception e) { e.printStackTrace(); return null; }}
 * do codigo original, que transformava falha em {@code 200} com corpo vazio.
 * Aqui toda excecao vira uma resposta com status correto, {@code type} estavel
 * para o cliente programar em cima, e mensagem util para quem esta usando a tela.
 *
 * <p>Detalhe interno (stack trace, SQL, nome de constraint) fica no log, nunca na
 * resposta.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private static final URI TIPO_VALIDACAO        = URI.create("urn:webgis:problema:validacao");
	private static final URI TIPO_NAO_ENCONTRADO   = URI.create("urn:webgis:problema:nao-encontrado");
	private static final URI TIPO_CONFLITO_DADOS   = URI.create("urn:webgis:problema:conflito-de-dados");
	private static final URI TIPO_CONFLITO_ESPACO  = URI.create("urn:webgis:problema:conflito-espacial");
	private static final URI TIPO_CORPO_INVALIDO   = URI.create("urn:webgis:problema:corpo-invalido");
	private static final URI TIPO_SATURADO         = URI.create("urn:webgis:problema:capacidade-excedida");
	private static final URI TIPO_INTERNO          = URI.create("urn:webgis:problema:erro-interno");

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	public ProblemDetail tratarNaoEncontrado(RecursoNaoEncontradoException ex) {
		ProblemDetail problema = criar(HttpStatus.NOT_FOUND, "Recurso nao encontrado", ex.getMessage(), TIPO_NAO_ENCONTRADO);
		problema.setProperty("recurso", ex.getRecurso());
		problema.setProperty("identificador", ex.getIdentificador());
		return problema;
	}

	@ExceptionHandler(ConflitoDeDadosException.class)
	public ProblemDetail tratarConflitoDeDados(ConflitoDeDadosException ex) {
		return criar(HttpStatus.CONFLICT, "Conflito de dados", ex.getMessage(), TIPO_CONFLITO_DADOS);
	}

	@ExceptionHandler(ConflitoEspacialException.class)
	public ProblemDetail tratarConflitoEspacial(ConflitoEspacialException ex) {
		ProblemDetail problema = criar(HttpStatus.CONFLICT, "Conflito espacial", ex.getMessage(), TIPO_CONFLITO_ESPACO);
		problema.setProperty("idImovelConflitante", ex.getIdImovelConflitante());
		return problema;
	}

	@ExceptionHandler(CapacidadeExcedidaException.class)
	public ResponseEntity<ProblemDetail> tratarCapacidadeExcedida(CapacidadeExcedidaException ex) {
		ProblemDetail problema = criar(HttpStatus.SERVICE_UNAVAILABLE, "Servico temporariamente indisponivel",
				ex.getMessage(), TIPO_SATURADO);
		problema.setProperty("segundosParaNovaTentativa", ex.getSegundosParaNovaTentativa());

		log.warn("Pool GIS saturado, tarefa recusada: {}", ex.getMessage());

		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosParaNovaTentativa()))
				.body(problema);
	}

	/**
	 * Unicidade violada em nivel de banco. Acontece quando duas requisicoes
	 * concorrentes passam pela checagem da aplicacao ao mesmo tempo — a
	 * constraint e quem decide, e o perdedor recebe 409 em vez de 500.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail tratarIntegridade(DataIntegrityViolationException ex) {
		log.warn("Violacao de integridade traduzida para 409", ex);
		return criar(HttpStatus.CONFLICT, "Conflito de dados",
				"A operacao viola uma restricao de integridade do cadastro.", TIPO_CONFLITO_DADOS);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ProblemDetail tratarConcorrencia(OptimisticLockingFailureException ex) {
		log.warn("Conflito de concorrencia", ex);
		return criar(HttpStatus.CONFLICT, "Conflito de concorrencia",
				"O registro foi alterado por outra operacao. Recarregue e tente novamente.", TIPO_CONFLITO_DADOS);
	}

	/** Validacao de {@code @RequestParam} / {@code @PathVariable}. */
	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail tratarViolacaoDeConstraint(ConstraintViolationException ex) {
		List<ErroDeCampo> erros = ex.getConstraintViolations().stream()
				.map(v -> new ErroDeCampo(v.getPropertyPath().toString(), v.getMessage()))
				.sorted(Comparator.comparing(ErroDeCampo::campo))
				.toList();

		ProblemDetail problema = criar(HttpStatus.BAD_REQUEST, "Parametros invalidos",
				"Um ou mais parametros da requisicao sao invalidos.", TIPO_VALIDACAO);
		problema.setProperty("erros", erros);
		return problema;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail tratarArgumentoInvalido(IllegalArgumentException ex) {
		return criar(HttpStatus.BAD_REQUEST, "Requisicao invalida", ex.getMessage(), TIPO_VALIDACAO);
	}

	/** Rede de seguranca. Qualquer coisa nao prevista vira 500 sem vazar detalhe. */
	@ExceptionHandler(Exception.class)
	public ProblemDetail tratarInesperado(Exception ex, WebRequest requisicao) {
		log.error("Erro nao tratado em {}", requisicao.getDescription(false), ex);
		return criar(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
				"Ocorreu um erro inesperado ao processar a requisicao.", TIPO_INTERNO);
	}

	// --- sobrescritas do handler padrao do Spring MVC -----------------------

	/** Corpo com campo invalido: devolve a lista de erros por campo. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {

		List<ErroDeCampo> erros = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> new ErroDeCampo(e.getField(), e.getDefaultMessage()))
				.sorted(Comparator.comparing(ErroDeCampo::campo))
				.toList();

		List<ErroDeCampo> errosGlobais = ex.getBindingResult().getGlobalErrors().stream()
				.map(e -> new ErroDeCampo(e.getObjectName(), e.getDefaultMessage()))
				.toList();

		ProblemDetail problema = criar(HttpStatus.BAD_REQUEST, "Dados invalidos",
				"Um ou mais campos do corpo da requisicao sao invalidos.", TIPO_VALIDACAO);
		problema.setProperty("erros", concatenar(erros, errosGlobais));

		return ResponseEntity.badRequest().body(problema);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {

		List<ErroDeCampo> erros = ex.getAllValidationResults().stream()
				.flatMap(resultado -> resultado.getResolvableErrors().stream()
						.map(erro -> new ErroDeCampo(
								resultado.getMethodParameter().getParameterName(),
								erro.getDefaultMessage())))
				.sorted(Comparator.comparing(ErroDeCampo::campo))
				.toList();

		ProblemDetail problema = criar(HttpStatus.BAD_REQUEST, "Parametros invalidos",
				"Um ou mais parametros da requisicao sao invalidos.", TIPO_VALIDACAO);
		problema.setProperty("erros", erros);

		return ResponseEntity.badRequest().body(problema);
	}

	/**
	 * Corpo ilegivel: JSON malformado, ou tipo incompativel.
	 *
	 * <p>No codigo original, {@code POST [1,2,3]} devolvia {@code 200} com corpo
	 * vazio porque o {@code ClassCastException} era engolido.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {

		ProblemDetail problema = criar(HttpStatus.BAD_REQUEST, "Corpo invalido",
				"Nao foi possivel interpretar o corpo da requisicao. Esperado um objeto JSON valido.",
				TIPO_CORPO_INVALIDO);

		return ResponseEntity.badRequest().body(problema);
	}

	/** {@code /api/imoveis/abc} — antes isso virava injecao de SQL, agora e 400. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(org.springframework.beans.TypeMismatchException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {

		String parametro = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : "parametro";

		ProblemDetail problema = criar(HttpStatus.BAD_REQUEST, "Parametro invalido",
				"O valor informado para '%s' nao tem o tipo esperado.".formatted(parametro), TIPO_VALIDACAO);

		return ResponseEntity.badRequest().body(problema);
	}

	// --- infraestrutura -----------------------------------------------------

	private static ProblemDetail criar(HttpStatus status, String titulo, String detalhe, URI tipo) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
		problema.setTitle(titulo);
		problema.setType(tipo);
		problema.setProperty("timestamp", Instant.now());
		return problema;
	}

	private static List<ErroDeCampo> concatenar(List<ErroDeCampo> a, List<ErroDeCampo> b) {
		if (b.isEmpty()) {
			return a;
		}
		return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
	}

	/** Erro de um campo especifico, para a interface destacar o input certo. */
	public record ErroDeCampo(String campo, String mensagem) {
	}
}
