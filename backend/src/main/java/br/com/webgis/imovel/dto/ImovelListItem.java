package br.com.webgis.imovel.dto;

import java.math.BigDecimal;

/**
 * Linha da listagem.
 *
 * <p>Deliberadamente menor que {@link ImovelResponse}: sem datas de auditoria e
 * sem dimensoes. Geometria nunca aparece aqui — o poligono so trafega pelo
 * endpoint de mapa, e mesmo la recortado por viewport.
 */
public record ImovelListItem(
		Long id,
		Long proprietarioId,
		String proprietarioNome,
		String municipio,
		String uf,
		String bairro,
		String rua,
		String numero,
		BigDecimal latitude,
		BigDecimal longitude,
		BigDecimal areaM2,
		boolean ativo) {
}
