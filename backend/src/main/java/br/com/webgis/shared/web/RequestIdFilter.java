package br.com.webgis.shared.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Da um identificador a cada requisicao e o coloca no MDC.
 *
 * <p>E o que permite ligar, no log, uma linha do controller a uma linha escrita
 * minutos depois dentro do pool GIS — o {@code TaskDecorator} do executor
 * carrega esse mesmo MDC para a thread do worker.
 *
 * <p>Respeita um {@code X-Request-Id} vindo de fora (proxy, gateway) para nao
 * quebrar o rastro quando ele ja existe.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String CABECALHO = "X-Request-Id";
	public static final String CHAVE_MDC = "requestId";

	private static final int TAMANHO_MAXIMO = 64;

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {

		String requestId = identificadorDe(requisicao);

		MDC.put(CHAVE_MDC, requestId);
		resposta.setHeader(CABECALHO, requestId);

		try {
			cadeia.doFilter(requisicao, resposta);
		} finally {
			MDC.remove(CHAVE_MDC);
		}
	}

	private static String identificadorDe(HttpServletRequest requisicao) {
		String recebido = requisicao.getHeader(CABECALHO);

		if (recebido == null || recebido.isBlank()) {
			return UUID.randomUUID().toString();
		}

		// Valor vindo de fora nao entra cru no log: corta o tamanho e remove o que
		// nao for seguro, para nao permitir injecao de conteudo nas linhas de log.
		return recebido.replaceAll("[^A-Za-z0-9\\-_.]", "")
				.substring(0, Math.min(recebido.length(), TAMANHO_MAXIMO));
	}
}
