package br.com.webgis.gis.dto;

import java.math.BigDecimal;

/**
 * Propriedades de um imovel no mapa.
 *
 * <p>Apenas o necessario para o popup e para o link de navegacao. Endereco
 * completo, datas e dimensoes ficam de fora: multiplicados por milhares de
 * feicoes, viram trafego que a tela nao usa.
 */
public record PropriedadesImovel(
		Long id,
		Long proprietarioId,
		String proprietarioNome,
		String municipio,
		String uf,
		BigDecimal areaM2,
		/** {@code true} quando a feicao e o poligono real, {@code false} quando e so o ponto. */
		boolean poligono) implements PropriedadesFeicao {
}
