package br.com.webgis.gis.worker;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Pool dedicado a tarefas GIS pesadas.
 *
 * <p><b>Por que separado das threads de requisicao.</b> Uma exportacao de
 * centenas de milhares de feicoes ocupa uma thread por minutos. Rodando no pool
 * do Tomcat, algumas exportacoes simultaneas consomem as threads que atendem o
 * CRUD e a aplicacao inteira para de responder. Isolado, o pior caso e a
 * exportacao ficar lenta ou ser recusada — o cadastro continua funcionando.
 *
 * <p><b>Backpressure.</b> A fila e limitada e a politica de rejeicao e
 * {@link ThreadPoolExecutor.AbortPolicy}. Fila ilimitada nao e "aceitar tudo": e
 * adiar a falha ate acabar a memoria, com todas as tarefas perdidas de uma vez.
 * Recusar cedo, com {@code 503} e {@code Retry-After}, e uma resposta honesta que
 * o cliente sabe tratar.
 */
@Configuration
public class GisWorkerConfig {

	private static final Logger log = LoggerFactory.getLogger(GisWorkerConfig.class);

	public static final String EXECUTOR_GIS = "gisWorkerExecutor";

	@Bean(name = EXECUTOR_GIS)
	public ThreadPoolTaskExecutor gisWorkerExecutor(GisWorkerProperties propriedades) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		executor.setCorePoolSize(propriedades.tamanhoNucleo());
		executor.setMaxPoolSize(propriedades.tamanhoMaximo());
		executor.setQueueCapacity(propriedades.capacidadeFila());
		executor.setThreadNamePrefix("gis-worker-");

		// Rejeita em vez de executar na thread do chamador: com CallerRunsPolicy,
		// a thread do Tomcat executaria a exportacao e o isolamento acima seria
		// perdido justamente sob carga.
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

		// Shutdown gracioso: para de aceitar, termina o que ja comecou e so entao
		// encerra. Sem isso, um deploy corta exportacoes no meio.
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(propriedades.timeoutTarefaSegundos());

		executor.setTaskDecorator(new PropagadorDeContexto());
		executor.initialize();

		log.info("Pool GIS iniciado: nucleo={}, maximo={}, fila={}",
				propriedades.tamanhoNucleo(), propriedades.tamanhoMaximo(), propriedades.capacidadeFila());

		return executor;
	}

	/**
	 * Metricas do pool: ocupacao, fila e total concluido.
	 *
	 * <p>Exposto como {@link MeterBinder}, e nao registrado dentro do {@code @Bean}
	 * do executor: injetar o {@code MeterRegistry} durante a criacao do executor
	 * antecipa a montagem de toda a infraestrutura de metricas (que por sua vez
	 * enxerga a {@code EntityManagerFactory}) para antes do Flyway rodar, e o
	 * contexto quebra com dependencia circular. Com {@code MeterBinder}, o Spring
	 * faz a ligacao quando o registry ja existe.
	 */
	@Bean
	public MeterBinder metricasDoPoolGis(
			@org.springframework.beans.factory.annotation.Qualifier(EXECUTOR_GIS) ThreadPoolTaskExecutor executor) {

		return registry -> {
			ThreadPoolExecutor pool = executor.getThreadPoolExecutor();

			Gauge.builder("webgis.gis.pool.ativas", pool, ThreadPoolExecutor::getActiveCount)
					.description("Tarefas GIS em execucao")
					.register(registry);

			Gauge.builder("webgis.gis.pool.fila", pool, p -> p.getQueue().size())
					.description("Tarefas GIS aguardando na fila")
					.register(registry);

			Gauge.builder("webgis.gis.pool.concluidas", pool, ThreadPoolExecutor::getCompletedTaskCount)
					.description("Tarefas GIS concluidas desde a subida")
					.register(registry);
		};
	}

	/**
	 * Leva o contexto de log (MDC) da thread que submeteu para a thread do pool.
	 *
	 * <p>Sem isso, o log da exportacao perde o {@code requestId} e fica impossivel
	 * ligar o que aconteceu no worker a requisicao que o disparou.
	 */
	static class PropagadorDeContexto implements TaskDecorator {

		@Override
		public Runnable decorate(Runnable tarefa) {
			Map<String, String> contextoDeOrigem = MDC.getCopyOfContextMap();

			return () -> {
				Map<String, String> contextoAnterior = MDC.getCopyOfContextMap();
				try {
					if (contextoDeOrigem != null) {
						MDC.setContextMap(contextoDeOrigem);
					}
					tarefa.run();
				} finally {
					// Threads do pool sao reaproveitadas: nao limpar vazaria o
					// contexto de uma tarefa para a seguinte.
					MDC.clear();
					if (contextoAnterior != null) {
						MDC.setContextMap(contextoAnterior);
					}
				}
			};
		}
	}

	/** Traduz a recusa do pool em uma excecao de dominio. */
	public static boolean ehRecusaDoPool(RuntimeException e) {
		return e instanceof RejectedExecutionException;
	}
}
