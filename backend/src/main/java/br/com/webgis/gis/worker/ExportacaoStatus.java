package br.com.webgis.gis.worker;

public enum ExportacaoStatus {

	/** Aceita e aguardando uma thread livre do pool. */
	NA_FILA,

	EXECUTANDO,

	CONCLUIDA,

	/** Interrompida por pedido do cliente ou por estouro do tempo maximo. */
	CANCELADA,

	FALHOU;

	public boolean finalizada() {
		return this == CONCLUIDA || this == CANCELADA || this == FALHOU;
	}
}
