package br.com.webgis.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origens autorizadas a chamar a API pelo navegador.
 *
 * <p>Substitui o {@code @CrossOrigin(origins = "*")} do controller original, que
 * autorizava qualquer site a usar a API com o navegador da vitima.
 *
 * <p>No compose, o Nginx serve o frontend e faz proxy de {@code /api} para o
 * backend, entao tudo fica na mesma origem e a lista pode ficar vazia — CORS
 * deixa de ser necessario. A configuracao existe para o desenvolvimento local,
 * onde o dev-server roda em {@code :4200} e o backend em {@code :8080}.
 */
@ConfigurationProperties(prefix = "webgis.cors")
public record CorsProperties(List<String> origensPermitidas) {

	public CorsProperties {
		origensPermitidas = origensPermitidas == null ? List.of() : List.copyOf(origensPermitidas);
	}
}
