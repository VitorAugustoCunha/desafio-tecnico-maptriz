package br.com.webgis.amostra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites da geracao de massa de teste.
 *
 * <p>{@code habilitada} existe porque este endpoint escreve muito e nao tem
 * caso de uso em producao: e uma ferramenta de demonstracao e de teste de
 * carga. Desligado, o controller nem chega a ser registrado — o caminho
 * responde {@code 404}, em vez de existir e recusar.
 */
@ConfigurationProperties(prefix = "webgis.amostra")
public record AmostraProperties(Boolean habilitada, Integer quantidadePadrao, Integer quantidadeMaxima) {

	public AmostraProperties {
		habilitada = habilitada == null || habilitada;
		quantidadePadrao = quantidadePadrao == null ? 1000 : quantidadePadrao;
		quantidadeMaxima = quantidadeMaxima == null ? 5000 : quantidadeMaxima;
	}

	/** Ajusta a quantidade pedida para dentro dos limites configurados. */
	public int ajustar(Integer quantidadePedida) {
		if (quantidadePedida == null || quantidadePedida < 1) {
			return quantidadePadrao;
		}
		return Math.min(quantidadePedida, quantidadeMaxima);
	}
}
