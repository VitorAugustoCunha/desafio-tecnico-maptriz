package br.com.webgis.gis.worker;

import java.time.Instant;
import java.util.UUID;

/** Status de uma exportacao. */
public record ExportacaoResponse(
		UUID id,
		ExportacaoStatus status,
		long feicoesEscritas,
		Instant criadaEm,
		String mensagemDeErro,
		/** {@code true} quando o arquivo ja pode ser baixado. */
		boolean prontaParaDownload) {

	public static ExportacaoResponse de(Exportacao exportacao) {
		return new ExportacaoResponse(
				exportacao.getId(),
				exportacao.getStatus(),
				exportacao.getFeicoesEscritas(),
				exportacao.getCriadaEm(),
				exportacao.getMensagemDeErro(),
				exportacao.getStatus() == ExportacaoStatus.CONCLUIDA);
	}
}
