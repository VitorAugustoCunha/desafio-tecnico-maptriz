package br.com.webgis.gis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.webgis.IntegracaoBase;
import br.com.webgis.imovel.ImovelService;
import br.com.webgis.imovel.dto.ImovelRequest;
import br.com.webgis.shared.error.ConflitoEspacialException;

/**
 * Race condition entre "consultar conflito" e "inserir".
 *
 * <p>Sem o advisory lock, duas transacoes simultaneas em READ COMMITTED nao
 * enxergam uma a outra: as duas consultam, as duas nao encontram conflito, e as
 * duas gravam. O cadastro termina com dois lotes no mesmo lugar, e nenhum erro
 * aparece em lugar nenhum.
 *
 * <p>O teste dispara N cadastros do <b>mesmo</b> retangulo ao mesmo tempo,
 * sincronizados por {@link CyclicBarrier} para que realmente colidam, e exige
 * que exatamente um sobreviva.
 *
 * <p>Nao ha {@code sleep} aqui: a barreira garante a simultaneidade e os
 * {@code Future} garantem que o resultado so e conferido depois que todas as
 * transacoes terminaram.
 */
@DisplayName("Concorrencia na validacao de sobreposicao")
class ConcorrenciaGeometriaTest extends IntegracaoBase {

	private static final int TENTATIVAS_SIMULTANEAS = 6;

	private static final BigDecimal LATITUDE = new BigDecimal("-25.4420");
	private static final BigDecimal LONGITUDE = new BigDecimal("-49.2920");

	@Autowired
	private ImovelService imoveis;

	@BeforeEach
	void limpar() {
		limparCadastro();
	}

	@Test
	@DisplayName("com N cadastros simultaneos na mesma area, exatamente um e aceito")
	void apenasUmCadastroSobrevive() throws Exception {
		CyclicBarrier largada = new CyclicBarrier(TENTATIVAS_SIMULTANEAS);
		ExecutorService threads = Executors.newFixedThreadPool(TENTATIVAS_SIMULTANEAS);

		AtomicInteger aceitos = new AtomicInteger();
		AtomicInteger conflitos = new AtomicInteger();
		AtomicInteger outrosErros = new AtomicInteger();

		try {
			List<Callable<Void>> tentativas = java.util.stream.IntStream.range(0, TENTATIVAS_SIMULTANEAS)
					.mapToObj(indice -> (Callable<Void>) () -> {
						// Todas as threads chegam aqui e so entao seguem juntas.
						largada.await(30, TimeUnit.SECONDS);

						try {
							imoveis.criar(requisicao("Titular " + indice));
							aceitos.incrementAndGet();
						} catch (ConflitoEspacialException e) {
							conflitos.incrementAndGet();
						} catch (RuntimeException e) {
							outrosErros.incrementAndGet();
						}
						return null;
					})
					.toList();

			for (Future<Void> resultado : threads.invokeAll(tentativas, 60, TimeUnit.SECONDS)) {
				resultado.get();
			}
		} finally {
			threads.shutdownNow();
		}

		assertThat(aceitos.get())
				.as("exatamente um cadastro pode ocupar a area")
				.isEqualTo(1);

		assertThat(conflitos.get())
				.as("os demais recebem conflito espacial (409), nao erro generico")
				.isEqualTo(TENTATIVAS_SIMULTANEAS - 1);

		assertThat(outrosErros.get()).as("nenhuma falha inesperada").isZero();

		assertThat(contar("SELECT count(*) FROM imovel WHERE geom IS NOT NULL"))
				.as("o banco confirma: um unico poligono gravado")
				.isEqualTo(1);

		assertThat(contar("SELECT count(*) FROM imovel"))
				.as("as transacoes recusadas fizeram rollback completo")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("cadastros simultaneos em areas distintas sao todos aceitos")
	void areasDistintasNaoSeBloqueiam() throws Exception {
		CyclicBarrier largada = new CyclicBarrier(TENTATIVAS_SIMULTANEAS);
		ExecutorService threads = Executors.newFixedThreadPool(TENTATIVAS_SIMULTANEAS);

		AtomicInteger aceitos = new AtomicInteger();

		try {
			List<Callable<Void>> tentativas = java.util.stream.IntStream.range(0, TENTATIVAS_SIMULTANEAS)
					.mapToObj(indice -> (Callable<Void>) () -> {
						largada.await(30, TimeUnit.SECONDS);

						// ~1 km de distancia entre cada um.
						BigDecimal latitude = LATITUDE.add(new BigDecimal("0.01").multiply(new BigDecimal(indice)));

						imoveis.criar(requisicao("Titular " + indice, latitude));
						aceitos.incrementAndGet();
						return null;
					})
					.toList();

			for (Future<Void> resultado : threads.invokeAll(tentativas, 60, TimeUnit.SECONDS)) {
				resultado.get();
			}
		} finally {
			threads.shutdownNow();
		}

		assertThat(aceitos.get())
				.as("o lock serializa a escrita, mas nao recusa quem nao conflita")
				.isEqualTo(TENTATIVAS_SIMULTANEAS);

		assertThat(contar("SELECT count(*) FROM imovel WHERE geom IS NOT NULL"))
				.isEqualTo(TENTATIVAS_SIMULTANEAS);
	}

	private static ImovelRequest requisicao(String proprietario) {
		return requisicao(proprietario, LATITUDE);
	}

	private static ImovelRequest requisicao(String proprietario, BigDecimal latitude) {
		return new ImovelRequest(proprietario, "Curitiba", "PR", "Batel", "Avenida do Batel", "1560",
				latitude, LONGITUDE, null, new BigDecimal("20"), new BigDecimal("50"), null, true);
	}
}
