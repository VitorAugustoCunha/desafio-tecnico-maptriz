package br.com.webgis.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de paginacao da API.
 *
 * <p>O teto existe para que o cliente nao consiga pedir {@code size=1000000} e
 * reproduzir, por outro caminho, o "carrega a tabela inteira" do codigo original.
 */
@ConfigurationProperties(prefix = "webgis.paginacao")
public record PaginacaoProperties(Integer tamanhoPadrao, Integer tamanhoMaximo) {

	public PaginacaoProperties {
		tamanhoPadrao = tamanhoPadrao == null ? 20 : tamanhoPadrao;
		tamanhoMaximo = tamanhoMaximo == null ? 100 : tamanhoMaximo;
	}

	/** Ajusta o tamanho pedido para dentro dos limites configurados. */
	public int ajustar(Integer tamanhoPedido) {
		if (tamanhoPedido == null || tamanhoPedido < 1) {
			return tamanhoPadrao;
		}
		return Math.min(tamanhoPedido, tamanhoMaximo);
	}
}
