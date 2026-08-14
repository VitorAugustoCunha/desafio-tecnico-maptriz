package br.com.webgis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base dos testes de integracao.
 *
 * <p>Roda contra <b>PostgreSQL com PostGIS de verdade</b>, nunca H2. Metade do
 * que este projeto faz — {@code ST_Intersects}, {@code ST_Transform}, coluna
 * gerada, GiST, {@code pg_trgm}, advisory lock — simplesmente nao existe em
 * banco em memoria. Um teste que passa no H2 e nao no Postgres nao prova nada.
 *
 * <p>Container unico para toda a suite (padrao singleton, iniciado no bloco
 * estatico e reaproveitado): subir um container por classe de teste dominaria o
 * tempo de execucao.
 *
 * <p>As consultas de apoio usam {@link JdbcTemplate}, e nao o
 * {@code EntityManager}: os metodos de teste nao passam por proxy do Spring,
 * entao {@code @Transactional} neles nao abriria transacao nenhuma — e escrita
 * via {@code EntityManager} sem transacao falha.
 */
@SpringBootTest
public abstract class IntegracaoBase {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("webgis_teste")
			.withUsername("webgis")
			.withPassword("webgis");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void configurar(DynamicPropertyRegistry registro) {
		registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registro.add("spring.datasource.username", POSTGRES::getUsername);
		registro.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	protected JdbcTemplate jdbc;

	/**
	 * Zera o cadastro entre testes.
	 *
	 * <p>{@code TRUNCATE ... RESTART IDENTITY} em vez de rollback de transacao
	 * porque parte da suite precisa de commits reais — o teste de concorrencia so
	 * faz sentido com duas transacoes de verdade disputando o mesmo espaco.
	 */
	protected void limparCadastro() {
		jdbc.execute("TRUNCATE TABLE imovel, proprietario RESTART IDENTITY CASCADE");
	}

	protected long contar(String sql, Object... parametros) {
		Long total = jdbc.queryForObject(sql, Long.class, parametros);
		return total == null ? 0 : total;
	}

	protected String consultarTexto(String sql, Object... parametros) {
		return jdbc.queryForObject(sql, String.class, parametros);
	}

	protected double consultarDouble(String sql, Object... parametros) {
		Double valor = jdbc.queryForObject(sql, Double.class, parametros);
		return valor == null ? 0 : valor;
	}

	protected boolean consultarBooleano(String sql, Object... parametros) {
		return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, parametros));
	}
}
