package br.com.webgis.imovel;

import java.math.BigDecimal;

import br.com.webgis.imovel.dto.PoligonoGeoJson;

/**
 * Dados editaveis de um imovel, ja normalizados e validados.
 *
 * <p>Serve de fronteira entre a camada web e o dominio: a entidade nunca recebe
 * um DTO de request diretamente, entao mudar o contrato HTTP nao obriga a mexer
 * na entidade (e vice-versa).
 */
public record DadosDoImovel(
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
		/** Poligono desenhado no mapa, quando o lote nao veio de largura x comprimento. */
		PoligonoGeoJson poligono,
		boolean ativo) {

	public boolean temDimensoes() {
		return larguraM != null && comprimentoM != null;
	}

	public boolean temPoligonoDesenhado() {
		return poligono != null;
	}
}
