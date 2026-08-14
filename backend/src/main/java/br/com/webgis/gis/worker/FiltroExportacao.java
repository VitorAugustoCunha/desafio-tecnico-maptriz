package br.com.webgis.gis.worker;

import jakarta.validation.constraints.Size;

/** Recorte da exportacao. Sem filtro, exporta o cadastro inteiro — em lotes. */
public record FiltroExportacao(
		Long proprietarioId,
		@Size(max = 120, message = "no maximo 120 caracteres") String municipio) {

	public FiltroExportacao {
		if (municipio != null) {
			String limpo = municipio.trim();
			municipio = limpo.isEmpty() ? null : limpo;
		}
	}

	/** Padrao LIKE do municipio, ou {@code null} quando nao ha filtro. */
	public String padraoMunicipio() {
		return municipio == null ? null : "%" + municipio + "%";
	}
}
