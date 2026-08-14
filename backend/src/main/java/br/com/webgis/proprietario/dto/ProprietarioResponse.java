package br.com.webgis.proprietario.dto;

import java.time.OffsetDateTime;

/** Detalhe de um proprietario. Os imoveis dele vem da listagem filtrada por {@code proprietarioId}. */
public record ProprietarioResponse(
		Long id,
		String nome,
		long quantidadeImoveis,
		OffsetDateTime criadoEm,
		OffsetDateTime atualizadoEm) {
}
