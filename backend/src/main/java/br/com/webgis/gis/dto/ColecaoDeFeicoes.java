package br.com.webgis.gis.dto;

import java.util.List;

/**
 * {@code FeatureCollection} GeoJSON com metadados de diagnostico.
 *
 * <p>{@code metadados} nao faz parte do GeoJSON padrao, mas fica fora do array
 * de feicoes e nao atrapalha nenhum leitor. O frontend usa para saber se esta
 * vendo agregados ou feicoes individuais, e se a resposta foi truncada pelo
 * limite do servidor.
 */
public record ColecaoDeFeicoes(String type, List<Feicao> features, Metadados metadados) {

	public static ColecaoDeFeicoes de(List<Feicao> feicoes, Metadados metadados) {
		return new ColecaoDeFeicoes("FeatureCollection", feicoes, metadados);
	}

	/**
	 * @param agregado  {@code true} quando as feicoes sao clusters, nao imoveis
	 * @param truncado  {@code true} quando o limite do servidor cortou o resultado
	 */
	public record Metadados(int zoom, boolean agregado, int total, boolean truncado, int limite) {
	}
}
