package br.com.webgis.amostra.dto;

import java.math.BigDecimal;

/**
 * Resultado da geracao de massa de teste.
 *
 * @param solicitados quantidade pedida, ja ajustada ao teto configurado
 * @param criados     lotes que entraram de fato
 * @param ignorados   lotes pulados por ja haver imovel ocupando aquele espaco
 * @param latitude    centro do conjunto de amostra, para a tela levar o mapa ate la
 */
public record AmostraResponse(
		int solicitados,
		int criados,
		int ignorados,
		String municipio,
		String bairro,
		BigDecimal latitude,
		BigDecimal longitude) {
}
