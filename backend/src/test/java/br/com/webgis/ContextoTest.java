package br.com.webgis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** Sobe o contexto completo. Se este teste passa, a fiacao da aplicacao esta sa. */
@DisplayName("Contexto da aplicacao")
class ContextoTest extends IntegracaoBase {

	@Autowired
	private ApplicationContext contexto;

	@Test
	@DisplayName("o contexto sobe com Flyway, JPA e o pool GIS")
	void contextoSobe() {
		assertThat(contexto.containsBean("gisWorkerExecutor")).isTrue();
		assertThat(contar("SELECT count(*) FROM flyway_schema_history")).isEqualTo(5);
	}
}
