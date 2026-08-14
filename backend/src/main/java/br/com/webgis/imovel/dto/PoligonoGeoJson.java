package br.com.webgis.imovel.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Poligono desenhado no mapa, em GeoJSON (WGS 84, EPSG:4326).
 *
 * <p>Tipado, e nao uma {@code String} com JSON cru: assim a Bean Validation
 * rejeita desenho malformado com {@code 400} <b>antes</b> de a consulta chegar
 * ao PostGIS. Mandar texto arbitrario para {@code ST_GeomFromGeoJSON} faria uma
 * entrada invalida virar erro de banco — ou seja, {@code 500} — e ainda
 * envenenaria a transacao.
 *
 * <p>Convencao do GeoJSON: cada posicao e {@code [longitude, latitude]}, nesta
 * ordem. O primeiro anel e o contorno externo; os demais, se houver, sao furos.
 */
public record PoligonoGeoJson(

		@NotNull(message = "informe o tipo da geometria")
		@Pattern(regexp = "Polygon", message = "apenas Polygon e aceito")
		String type,

		@NotNull(message = "informe as coordenadas do poligono")
		@NotEmpty(message = "o poligono precisa de ao menos um anel")
		List<List<List<BigDecimal>>> coordinates) {

	/** Um anel fechado precisa de no minimo 4 posicoes (a ultima repete a primeira). */
	private static final int MINIMO_DE_POSICOES = 4;

	@AssertTrue(message = "cada anel do poligono precisa de ao menos 4 pontos e deve ser fechado")
	@JsonIgnore
	public boolean isAneisFechados() {
		if (coordinates == null) {
			return true; // @NotNull ja reporta
		}

		for (List<List<BigDecimal>> anel : coordinates) {
			if (anel == null || anel.size() < MINIMO_DE_POSICOES) {
				return false;
			}

			List<BigDecimal> primeira = anel.get(0);
			List<BigDecimal> ultima = anel.get(anel.size() - 1);

			if (!posicaoValida(primeira) || !posicaoValida(ultima)) {
				return false;
			}
			// Fechado: a ultima posicao precisa coincidir com a primeira.
			if (primeira.get(0).compareTo(ultima.get(0)) != 0
					|| primeira.get(1).compareTo(ultima.get(1)) != 0) {
				return false;
			}
		}

		return true;
	}

	@AssertTrue(message = "as coordenadas devem estar entre -180/180 (longitude) e -90/90 (latitude)")
	@JsonIgnore
	public boolean isCoordenadasNaFaixa() {
		if (coordinates == null) {
			return true;
		}

		return coordinates.stream()
				.filter(anel -> anel != null)
				.flatMap(List::stream)
				.allMatch(PoligonoGeoJson::posicaoValida);
	}

	private static boolean posicaoValida(List<BigDecimal> posicao) {
		if (posicao == null || posicao.size() < 2) {
			return false;
		}

		BigDecimal longitude = posicao.get(0);
		BigDecimal latitude = posicao.get(1);

		if (longitude == null || latitude == null) {
			return false;
		}

		return longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
				&& longitude.compareTo(BigDecimal.valueOf(180)) <= 0
				&& latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
				&& latitude.compareTo(BigDecimal.valueOf(90)) <= 0;
	}
}
