package br.com.webgis.gis.worker;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado de uma exportacao GeoJSON.
 *
 * <p>E lido pela thread da requisicao ({@code GET /api/exportacoes/{id}}) e
 * escrito pela thread do pool, entao todo campo mutavel e atomico. Sem isso, o
 * cliente poderia consultar o status e ver um valor desatualizado por tempo
 * indefinido.
 */
public class Exportacao {

	private final UUID id = UUID.randomUUID();
	private final Instant criadaEm = Instant.now();
	private final FiltroExportacao filtro;

	private final AtomicReference<ExportacaoStatus> status = new AtomicReference<>(ExportacaoStatus.NA_FILA);
	private final AtomicReference<String> mensagemDeErro = new AtomicReference<>();
	private final AtomicReference<Path> arquivo = new AtomicReference<>();
	private final AtomicLong feicoesEscritas = new AtomicLong();
	private final AtomicBoolean cancelamentoSolicitado = new AtomicBoolean();

	public Exportacao(FiltroExportacao filtro) {
		this.filtro = filtro;
	}

	public UUID getId() {
		return id;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

	public FiltroExportacao getFiltro() {
		return filtro;
	}

	public ExportacaoStatus getStatus() {
		return status.get();
	}

	public void marcar(ExportacaoStatus novoStatus) {
		status.set(novoStatus);
	}

	public void falhar(String mensagem) {
		mensagemDeErro.set(mensagem);
		status.set(ExportacaoStatus.FALHOU);
	}

	public String getMensagemDeErro() {
		return mensagemDeErro.get();
	}

	public Path getArquivo() {
		return arquivo.get();
	}

	public void setArquivo(Path caminho) {
		arquivo.set(caminho);
	}

	public long getFeicoesEscritas() {
		return feicoesEscritas.get();
	}

	public void somarFeicoes(long quantidade) {
		feicoesEscritas.addAndGet(quantidade);
	}

	/** Cancelamento logico: o laco de lotes checa esta flag entre um lote e outro. */
	public void solicitarCancelamento() {
		cancelamentoSolicitado.set(true);
	}

	public boolean cancelamentoSolicitado() {
		return cancelamentoSolicitado.get();
	}
}
