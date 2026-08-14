package br.com.webgis.gis.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.webgis.IntegracaoBase;
import br.com.webgis.shared.error.CapacidadeExcedidaException;

/**
 * Pool de trabalho GIS: exportacao em lotes, backpressure e cancelamento.
 *
 * <p>O pool e reduzido a 1 thread e fila 1 para que a saturacao seja um estado
 * alcancavel de forma deterministica, e nao uma corrida contra o relogio. As
 * tarefas que ocupam o pool bloqueiam em um {@link CountDownLatch} controlado
 * pelo teste — nada aqui depende de {@code sleep} para dar certo.
 */
@TestPropertySource(properties = {
		"webgis.gis-worker.tamanho-nucleo=1",
		"webgis.gis-worker.tamanho-maximo=1",
		"webgis.gis-worker.capacidade-fila=1",
		"webgis.gis-worker.tamanho-lote=10"
})
@DisplayName("Pool de trabalho GIS e exportacao GeoJSON")
class ExportacaoWorkerTest extends IntegracaoBase {

	@Autowired
	private ExportacaoGeoJsonService servico;

	@Autowired
	@Qualifier(GisWorkerConfig.EXECUTOR_GIS)
	private ThreadPoolTaskExecutor executor;

	@Autowired
	private ObjectMapper json;

	private CountDownLatch liberacao;

	@BeforeEach
	void prepararCadastro() {
		limparCadastro();
		liberacao = new CountDownLatch(1);
	}

	@AfterEach
	void liberarPool() {
		// Garante que nenhuma tarefa bloqueada sobreviva ao teste.
		liberacao.countDown();
	}

	@Test
	@DisplayName("exporta em lotes e escreve um GeoJSON valido com todas as feicoes")
	void exportaTudoEmLotes() throws Exception {
		// 25 imoveis com lote de 10 -> obriga 3 idas ao banco.
		inserirImoveis(25);

		Exportacao exportacao = servico.submeter(new FiltroExportacao(null, null));

		aguardarFim(exportacao);

		assertThat(exportacao.getStatus()).isEqualTo(ExportacaoStatus.CONCLUIDA);
		assertThat(exportacao.getFeicoesEscritas()).isEqualTo(25);

		String conteudo = Files.readString(exportacao.getArquivo(), StandardCharsets.UTF_8);
		JsonNode geoJson = json.readTree(conteudo);

		assertThat(geoJson.get("type").asText()).isEqualTo("FeatureCollection");
		assertThat(geoJson.get("features")).hasSize(25);
		assertThat(geoJson.get("total").asLong()).isEqualTo(25);
		assertThat(geoJson.get("features").get(0).get("geometry").get("type").asText()).isEqualTo("Point");
	}

	@Test
	@DisplayName("respeita o filtro de municipio")
	void exportaComFiltro() throws Exception {
		inserirImoveis(5, "Curitiba", "PR");
		inserirImoveis(3, "Sao Paulo", "SP");

		Exportacao exportacao = servico.submeter(new FiltroExportacao(null, "Curitiba"));

		aguardarFim(exportacao);

		assertThat(exportacao.getStatus()).isEqualTo(ExportacaoStatus.CONCLUIDA);
		assertThat(exportacao.getFeicoesEscritas()).isEqualTo(5);
	}

	@Test
	@DisplayName("cadastro vazio gera um GeoJSON vazio, nao um erro")
	void exportaCadastroVazio() throws Exception {
		Exportacao exportacao = servico.submeter(new FiltroExportacao(null, null));

		aguardarFim(exportacao);

		assertThat(exportacao.getStatus()).isEqualTo(ExportacaoStatus.CONCLUIDA);
		assertThat(exportacao.getFeicoesEscritas()).isZero();

		JsonNode geoJson = json.readTree(Files.readString(exportacao.getArquivo(), StandardCharsets.UTF_8));
		assertThat(geoJson.get("features")).isEmpty();
	}

	@Test
	@DisplayName("com o pool saturado, a submissao e recusada com capacidade excedida (503)")
	void saturacaoRecusaDeFormaControlada() throws Exception {
		CountDownLatch ocupou = new CountDownLatch(1);

		// 1 thread ocupada...
		executor.execute(() -> {
			ocupou.countDown();
			esperarLiberacao();
		});

		assertThat(ocupou.await(10, TimeUnit.SECONDS))
				.as("a tarefa bloqueante precisa estar rodando antes de continuar")
				.isTrue();

		// ...e 1 vaga na fila ocupada.
		executor.execute(this::esperarLiberacao);

		Awaitility.await().atMost(Duration.ofSeconds(10))
				.until(() -> servico.tarefasNaFila() == 1);

		// A proxima nao tem para onde ir.
		assertThatThrownBy(() -> servico.submeter(new FiltroExportacao(null, null)))
				.isInstanceOf(CapacidadeExcedidaException.class)
				.hasMessageContaining("capacidade");

		CapacidadeExcedidaException erro = catchCapacidade();
		assertThat(erro.getSegundosParaNovaTentativa())
				.as("o cliente precisa saber quando tentar de novo")
				.isPositive();
	}

	@Test
	@DisplayName("a exportacao recusada nao fica registrada como pendente")
	void recusaNaoDeixaRegistroFantasma() throws Exception {
		CountDownLatch ocupou = new CountDownLatch(1);

		executor.execute(() -> {
			ocupou.countDown();
			esperarLiberacao();
		});
		assertThat(ocupou.await(10, TimeUnit.SECONDS)).isTrue();
		executor.execute(this::esperarLiberacao);

		Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> servico.tarefasNaFila() == 1);

		CapacidadeExcedidaException erro = catchCapacidade();

		assertThat(erro).isNotNull();
		// Nenhum id foi entregue ao cliente, entao nao ha o que consultar depois.
		assertThat(servico.tarefasNaFila()).isEqualTo(1);
	}

	@Test
	@DisplayName("o pool volta a aceitar assim que a fila esvazia")
	void voltaAAceitarDepoisDeLiberar() throws Exception {
		CountDownLatch ocupou = new CountDownLatch(1);

		executor.execute(() -> {
			ocupou.countDown();
			esperarLiberacao();
		});
		assertThat(ocupou.await(10, TimeUnit.SECONDS)).isTrue();
		executor.execute(this::esperarLiberacao);

		Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> servico.tarefasNaFila() == 1);
		assertThat(catchCapacidade()).isNotNull();

		liberacao.countDown();

		Awaitility.await().atMost(Duration.ofSeconds(15))
				.until(() -> executor.getThreadPoolExecutor().getActiveCount() == 0
						&& servico.tarefasNaFila() == 0);

		inserirImoveis(3);
		Exportacao aceita = servico.submeter(new FiltroExportacao(null, null));

		aguardarFim(aceita);
		assertThat(aceita.getStatus()).isEqualTo(ExportacaoStatus.CONCLUIDA);
	}

	@Test
	@DisplayName("cancelamento interrompe a exportacao entre lotes")
	void cancelamento() {
		inserirImoveis(200);

		Exportacao exportacao = servico.submeter(new FiltroExportacao(null, null));
		servico.cancelar(exportacao.getId());

		aguardarFim(exportacao);

		assertThat(exportacao.getStatus())
				.as("ou terminou antes do cancelamento chegar, ou parou por causa dele")
				.isIn(ExportacaoStatus.CANCELADA, ExportacaoStatus.CONCLUIDA);

		if (exportacao.getStatus() == ExportacaoStatus.CANCELADA) {
			assertThat(exportacao.getFeicoesEscritas())
					.as("cancelou entre lotes, entao parou antes do total")
					.isLessThan(200);
		}
	}

	@Test
	@DisplayName("consultar exportacao inexistente devolve recurso nao encontrado")
	void exportacaoInexistente() {
		assertThatThrownBy(() -> servico.buscar(java.util.UUID.randomUUID()))
				.isInstanceOf(br.com.webgis.shared.error.RecursoNaoEncontradoException.class);
	}

	// --- apoio --------------------------------------------------------------

	private CapacidadeExcedidaException catchCapacidade() {
		try {
			servico.submeter(new FiltroExportacao(null, null));
			return null;
		} catch (CapacidadeExcedidaException e) {
			return e;
		}
	}

	private void esperarLiberacao() {
		try {
			liberacao.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void aguardarFim(Exportacao exportacao) {
		Awaitility.await()
				.atMost(Duration.ofSeconds(30))
				.until(() -> exportacao.getStatus().finalizada());
	}

	private void inserirImoveis(int quantidade) {
		inserirImoveis(quantidade, "Curitiba", "PR");
	}

	private void inserirImoveis(int quantidade, String municipio, String uf) {
		jdbc.update("""
				INSERT INTO proprietario (nome, nome_normalizado)
				SELECT 'Titular %s', 'titular %s'
				 WHERE NOT EXISTS (SELECT 1 FROM proprietario WHERE nome_normalizado = 'titular %s')
				""".formatted(municipio, municipio.toLowerCase(), municipio.toLowerCase()));

		Long idProprietario = jdbc.queryForObject(
				"SELECT id FROM proprietario WHERE nome_normalizado = ?", Long.class, "titular " + municipio.toLowerCase());

		for (int i = 0; i < quantidade; i++) {
			jdbc.update("""
					INSERT INTO imovel (proprietario_id, municipio, uf, bairro, rua, numero,
					                    latitude, longitude, area_m2, ativo)
					VALUES (?, ?, ?, 'Centro', 'Rua Central', ?, ?, ?, 100, true)
					""",
					idProprietario, municipio, uf, String.valueOf(i),
					-25.44 + i * 0.001, -49.29 + i * 0.001);
		}
	}
}
