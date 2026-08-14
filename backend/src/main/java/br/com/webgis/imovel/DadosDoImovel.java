package br.com.webgis.imovel;

import java.math.BigDecimal;

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
		boolean ativo) {
}
