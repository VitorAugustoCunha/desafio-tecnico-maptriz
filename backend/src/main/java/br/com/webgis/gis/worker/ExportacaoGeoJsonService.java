package br.com.webgis.gis.worker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.webgis.gis.MapaRepository;
import br.com.webgis.gis.dto.Feicao;
import br.com.webgis.shared.error.CapacidadeExcedidaException;
import br.com.webgis.shared.error.RecursoNaoEncontradoException;
import jakarta.annotation.PreDestroy;

/**
 * Exportacao de imoveis em GeoJSON, executada no pool GIS.
 *
 * <p>E o caso de uso que justifica o pool: o trabalho e longo, custoso e nao
 * pode ocupar as threads que atendem o CRUD. Nao ha nada aqui que precise ser
 * sincrono para o usuario — diferente da checagem de sobreposicao, que continua
 * dentro da transacao de escrita.
 *
 * <p><b>Streaming em lotes.</b> As feicoes sao lidas em blocos por keyset
 * ({@code id > ultimoId}) e escritas direto no arquivo. Em nenhum momento o
 * dataset inteiro fica na memoria — exportar 500 mil imoveis consome o mesmo
 * tanto de heap que exportar 500.
 *
 * <p><b>Transacao.</b> Cada lote abre a sua propria transacao de leitura
 * <i>dentro</i> da thread do worker, via {@link TransactionTemplate}. A
 * transacao nunca e herdada de quem submeteu (nem poderia: a submissao ja
 * retornou). Uma unica transacao para a exportacao inteira tambem seria ruim —
 * seguraria a conexao e o snapshot por minutos.
 *
 * <p><b>Limitacao conhecida:</b> o registro das exportacoes e em memoria e os
 * arquivos ficam em disco local, entao isto assume uma instancia. Com varias
 * replicas, o estado precisaria ir para banco/Redis e os arquivos para um object
 * storage. Esta anotado em docs/DECISIONS.md, ADR-006.
 */
@Service
public class ExportacaoGeoJsonService {

	private static final Logger log = LoggerFactory.getLogger(ExportacaoGeoJsonService.class);

	private final ThreadPoolTaskExecutor executor;
	private final MapaRepository repositorio;
	private final TransactionTemplate transacao;
	private final GisWorkerProperties propriedades;
	private final ObjectMapper mapper;

	private final Map<UUID, Exportacao> exportacoes = new ConcurrentHashMap<>();

	public ExportacaoGeoJsonService(
			@Qualifier(GisWorkerConfig.EXECUTOR_GIS) ThreadPoolTaskExecutor executor,
			MapaRepository repositorio,
			PlatformTransactionManager gerenciadorDeTransacao,
			GisWorkerProperties propriedades,
			ObjectMapper mapper) {

		this.executor = executor;
		this.repositorio = repositorio;
		this.propriedades = propriedades;
		this.mapper = mapper;

		// Template proprio, e nao o bean compartilhado do Spring: marcar readOnly
		// no bean global mudaria o comportamento de todo mundo que o injeta.
		this.transacao = new TransactionTemplate(gerenciadorDeTransacao);
		this.transacao.setReadOnly(true);
		this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Aceita a exportacao e devolve na hora.
	 *
	 * @throws CapacidadeExcedidaException quando a fila esta cheia (backpressure)
	 */
	public Exportacao submeter(FiltroExportacao filtro) {
		Exportacao exportacao = new Exportacao(filtro);

		// Registra antes de submeter: a thread do pool pode comecar a rodar dentro
		// do proprio execute(), e o status precisa ja estar consultavel.
		exportacoes.put(exportacao.getId(), exportacao);

		try {
			executor.execute(() -> processar(exportacao));
		} catch (RejectedExecutionException e) {
			exportacoes.remove(exportacao.getId());

			log.warn("Exportacao recusada: fila cheia ({} aguardando, {} ativas)",
					executor.getThreadPoolExecutor().getQueue().size(),
					executor.getActiveCount());

			throw new CapacidadeExcedidaException(
					"Nao ha capacidade para novas exportacoes no momento. Tente novamente em instantes.",
					propriedades.segundosParaNovaTentativa());
		}

		log.info("Exportacao {} aceita", exportacao.getId());

		return exportacao;
	}

	public Exportacao buscar(UUID id) {
		Exportacao exportacao = exportacoes.get(id);
		if (exportacao == null) {
			throw new RecursoNaoEncontradoException("Exportacao", id);
		}
		return exportacao;
	}

	public void cancelar(UUID id) {
		Exportacao exportacao = buscar(id);
		exportacao.solicitarCancelamento();
		log.info("Cancelamento solicitado para a exportacao {}", id);
	}

	// --- execucao no pool ---------------------------------------------------

	private void processar(Exportacao exportacao) {
		exportacao.marcar(ExportacaoStatus.EXECUTANDO);

		Instant limite = Instant.now().plus(Duration.ofSeconds(propriedades.timeoutTarefaSegundos()));
		Path destino = null;

		try {
			destino = Files.createTempFile("webgis-exportacao-", ".geojson");
			exportacao.setArquivo(destino);

			escrever(exportacao, destino, limite);

			if (exportacao.getStatus() == ExportacaoStatus.EXECUTANDO) {
				exportacao.marcar(ExportacaoStatus.CONCLUIDA);
				log.info("Exportacao {} concluida com {} feicoes", exportacao.getId(), exportacao.getFeicoesEscritas());
			}
		} catch (IOException | UncheckedIOException e) {
			log.error("Falha de escrita na exportacao {}", exportacao.getId(), e);
			exportacao.falhar("Nao foi possivel gravar o arquivo da exportacao.");
			apagar(destino);
		} catch (RuntimeException e) {
			log.error("Falha na exportacao {}", exportacao.getId(), e);
			exportacao.falhar("A exportacao falhou durante o processamento.");
			apagar(destino);
		}
	}

	private void escrever(Exportacao exportacao, Path destino, Instant limite) throws IOException {
		try (BufferedWriter escritor = Files.newBufferedWriter(destino, StandardCharsets.UTF_8);
				JsonGenerator json = mapper.getFactory().createGenerator(escritor)) {

			json.setCodec(mapper);
			json.writeStartObject();
			json.writeStringField("type", "FeatureCollection");
			json.writeArrayFieldStart("features");

			long ultimoId = 0;
			int tamanhoLote = propriedades.tamanhoLote();

			while (true) {
				if (exportacao.cancelamentoSolicitado()) {
					exportacao.marcar(ExportacaoStatus.CANCELADA);
					log.info("Exportacao {} cancelada apos {} feicoes",
							exportacao.getId(), exportacao.getFeicoesEscritas());
					break;
				}

				if (Instant.now().isAfter(limite)) {
					exportacao.falhar("A exportacao excedeu o tempo maximo de %d segundos."
							.formatted(propriedades.timeoutTarefaSegundos()));
					log.warn("Exportacao {} abortada por timeout", exportacao.getId());
					break;
				}

				long idMinimo = ultimoId;
				List<Feicao> lote = lerLote(exportacao.getFiltro(), idMinimo, tamanhoLote);

				if (lote.isEmpty()) {
					break;
				}

				for (Feicao feicao : lote) {
					json.writeObject(feicao);
				}

				exportacao.somarFeicoes(lote.size());
				ultimoId = MapaRepository.ultimoId(lote);

				// O lote menor que o pedido significa que acabou.
				if (lote.size() < tamanhoLote) {
					break;
				}
			}

			json.writeEndArray();
			json.writeNumberField("total", exportacao.getFeicoesEscritas());
			json.writeEndObject();
			json.flush();
		}
	}

	/** Cada lote em sua propria transacao de leitura, aberta aqui dentro do worker. */
	private List<Feicao> lerLote(FiltroExportacao filtro, long idMinimo, int tamanho) {
		return transacao.execute(status ->
				repositorio.loteParaExportacao(idMinimo, filtro.proprietarioId(), filtro.padraoMunicipio(), tamanho));
	}

	private static void apagar(Path caminho) {
		if (caminho == null) {
			return;
		}
		try {
			Files.deleteIfExists(caminho);
		} catch (IOException e) {
			log.warn("Nao foi possivel apagar o arquivo temporario {}", caminho, e);
		}
	}

	/** Nao deixa arquivo temporario para tras quando a aplicacao encerra. */
	@PreDestroy
	void limparArquivos() {
		exportacoes.values().forEach(exportacao -> apagar(exportacao.getArquivo()));
		exportacoes.clear();
	}

	/** Usado pelos testes e pelo endpoint de status para inspecionar a fila. */
	public int tarefasNaFila() {
		return executor.getThreadPoolExecutor().getQueue().size();
	}
}
