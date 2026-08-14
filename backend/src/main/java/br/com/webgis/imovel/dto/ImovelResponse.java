package br.com.webgis.imovel.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonRawValue;

/** Representacao completa de um imovel. Usada no detalhe, na criacao e na atualizacao. */
public record ImovelResponse(
		Long id,
		ProprietarioResumo proprietario,
		String municipio,
		String uf,
		String bairro,
		String rua,
		String numero,
		BigDecimal latitude,
		BigDecimal longitude,
		BigDecimal areaM2,
		BigDecimal larguraM,
		BigDecimal comprimentoM,
		/** {@code true} quando existe poligono em EPSG:31982 gravado para este imovel. */
		boolean possuiGeometria,
		/**
		 * Poligono do lote em GeoJSON (reprojetado para 4326), ou {@code null}.
		 *
		 * <p>Vai como JSON cru para a tela de edicao redesenhar a forma exata que
		 * esta gravada. So aparece no detalhe — a listagem nunca carrega geometria.
		 */
		@JsonRawValue String geometria,
		boolean ativo,
		OffsetDateTime criadoEm,
		OffsetDateTime atualizadoEm) {

	public record ProprietarioResumo(Long id, String nome) {
	}
}
