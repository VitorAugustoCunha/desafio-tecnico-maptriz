package br.com.webgis.imovel.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
		boolean ativo,
		OffsetDateTime criadoEm,
		OffsetDateTime atualizadoEm) {

	public record ProprietarioResumo(Long id, String nome) {
	}
}
