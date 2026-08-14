package br.com.webgis.gis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param limiteFeicoes     teto de feicoes por consulta de viewport
 * @param zoomMinimoDetalhe abaixo deste zoom o servidor devolve agregados
 */
@ConfigurationProperties(prefix = "webgis.mapa")
public record MapaProperties(Integer limiteFeicoes, Integer zoomMinimoDetalhe) {

	public MapaProperties {
		limiteFeicoes = limiteFeicoes == null ? 2000 : limiteFeicoes;
		zoomMinimoDetalhe = zoomMinimoDetalhe == null ? 12 : zoomMinimoDetalhe;
	}
}
