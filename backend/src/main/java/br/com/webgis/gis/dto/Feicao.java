package br.com.webgis.gis.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * Feicao GeoJSON.
 *
 * <p>{@code geometry} chega do PostGIS ja como texto GeoJSON ({@code ST_AsGeoJSON})
 * e vai para a resposta com {@code @JsonRawValue}, sem passar por desserializacao
 * e reserializacao. Alem de mais rapido, evita perda de precisao no caminho.
 */
public record Feicao(String type, @JsonRawValue String geometry, PropriedadesFeicao properties) {

	public static Feicao de(String geometriaGeoJson, PropriedadesFeicao propriedades) {
		return new Feicao("Feature", geometriaGeoJson, propriedades);
	}
}
