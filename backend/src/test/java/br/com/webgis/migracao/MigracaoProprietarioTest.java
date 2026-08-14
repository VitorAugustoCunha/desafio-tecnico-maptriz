package br.com.webgis.migracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prova que a migracao do proprietario nao perde dado (tarefa 4).
 *
 * <p>Container proprio, e migracao aplicada em duas etapas: primeiro ate a V2
 * (base legada, proprietario como texto), depois o resto. E a unica forma de
 * testar de verdade o "antes e depois" — em um banco ja totalmente migrado, a
 * coluna antiga nem existe mais.
 *
 * <p>Alem dos 12 registros originais, o teste insere casos que a base real tem e
 * o seed nao cobre: mesmo nome com espacamento diferente, mesmo nome em caixa
 * diferente, e imovel sem proprietario.
 */
@DisplayName("Migracao de proprietario para entidade propria")
class MigracaoProprietarioTest {

	private static PostgreSQLContainer<?> postgres;
	private static DataSource dataSource;

	@BeforeAll
	static void iniciar() {
		postgres = new PostgreSQLContainer<>(
				DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
				.withDatabaseName("webgis_migracao")
				.withUsername("webgis")
				.withPassword("webgis");
		postgres.start();

		PGSimpleDataSource ds = new PGSimpleDataSource();
		ds.setUrl(postgres.getJdbcUrl());
		ds.setUser(postgres.getUsername());
		ds.setPassword(postgres.getPassword());
		dataSource = ds;
	}

	@AfterAll
	static void encerrar() {
		if (postgres != null) {
			postgres.stop();
		}
	}

	/** Cada teste parte de um banco realmente vazio, sem depender da ordem de execucao. */
	@BeforeEach
	void zerarSchema() throws SQLException {
		executar("DROP SCHEMA IF EXISTS public CASCADE");
		executar("CREATE SCHEMA public");
	}

	@Test
	@DisplayName("preserva todos os nomes, deduplica com seguranca e nao deixa imovel orfao")
	void migracaoNaoPerdeDado() throws SQLException {
		// --- estado legado: schema + dados com proprietario em texto ------------
		flyway("2").migrate();

		int imoveisAntes = contar("SELECT count(*) FROM imovel");
		assertThat(imoveisAntes).as("seed legado da V2").isEqualTo(12);

		// Casos que a base real produz e o seed nao cobre.
		executar("""
				INSERT INTO imovel (proprietario, municipio, uf, bairro, rua, numero,
				                    latitude, longitude, area_m2, ativo)
				VALUES
				  ('  Maria   Aparecida  Souza ', 'Campinas', 'SP', 'Centro', 'Rua X', '1',
				   -22.9, -47.06, 100, true),
				  ('MARIA APARECIDA SOUZA',       'Santos',   'SP', 'Centro', 'Rua Y', '2',
				   -23.96, -46.33, 120, true),
				  ('Zeferino Sem Par',            'Osasco',   'SP', 'Centro', 'Rua Z', '3',
				   -23.53, -46.79, 90, true),
				  ('   ',                         'Barueri',  'SP', 'Centro', 'Rua W', '4',
				   -23.51, -46.88, 80, true)
				""");

		int imoveisTotais = contar("SELECT count(*) FROM imovel");
		assertThat(imoveisTotais).isEqualTo(16);

		List<String> nomesAntes = consultarTextos(
				"SELECT DISTINCT lower(btrim(regexp_replace(proprietario, '\\s+', ' ', 'g'))) "
						+ "FROM imovel WHERE btrim(proprietario) <> '' ORDER BY 1");

		// --- aplica a migracao do proprietario ---------------------------------
		flyway(null).migrate();

		// 1. nenhum imovel se perdeu
		assertThat(contar("SELECT count(*) FROM imovel"))
				.as("nenhum imovel pode sumir na migracao")
				.isEqualTo(imoveisTotais);

		// 2. nenhum imovel ficou sem titular
		assertThat(contar("SELECT count(*) FROM imovel WHERE proprietario_id IS NULL"))
				.as("nenhum imovel orfao")
				.isZero();

		// 3. a coluna textual so foi removida depois da validacao
		assertThat(colunaExiste("imovel", "proprietario"))
				.as("coluna textual antiga removida ao final")
				.isFalse();

		// 4. todo nome distinto virou exatamente um proprietario
		List<String> nomesDepois = consultarTextos("SELECT nome_normalizado FROM proprietario ORDER BY 1");

		assertThat(nomesDepois)
				.as("todos os nomes da base legada continuam representados")
				.containsAll(nomesAntes);

		// 5. as tres grafias de 'Maria Aparecida Souza' viraram UM titular com TRES imoveis
		int idMaria = contar(
				"SELECT id FROM proprietario WHERE nome_normalizado = 'maria aparecida souza'");
		assertThat(contar("SELECT count(*) FROM imovel WHERE proprietario_id = " + idMaria))
				.as("espacamento e caixa diferentes deduplicam para o mesmo titular")
				.isEqualTo(3);

		// 6. a grafia canonica preservada e a da linha mais antiga
		assertThat(consultarTextos(
				"SELECT nome FROM proprietario WHERE nome_normalizado = 'maria aparecida souza'"))
				.containsExactly("Maria Aparecida Souza");

		// 7. o imovel sem proprietario ganhou um titular explicito, em vez de ser descartado
		assertThat(contar(
				"SELECT count(*) FROM proprietario WHERE nome = 'Proprietário não informado'"))
				.as("imovel sem titular nao pode bloquear a migracao nem sumir")
				.isEqualTo(1);

		// 8. a FK esta valendo
		assertThat(contar("""
				SELECT count(*) FROM information_schema.table_constraints
				 WHERE table_name = 'imovel' AND constraint_name = 'fk_imovel_proprietario'
				"""))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("aplica todas as migrations em banco vazio")
	void migracaoEmBancoVazio() throws SQLException {
		flyway(null).migrate();

		assertThat(contar("SELECT count(*) FROM imovel")).isEqualTo(12);
		assertThat(contar("SELECT count(*) FROM proprietario")).isEqualTo(12);
		assertThat(contar("SELECT count(*) FROM imovel WHERE proprietario_id IS NULL")).isZero();

		// PostGIS habilitado e geometria disponivel
		assertThat(contar("SELECT count(*) FROM pg_extension WHERE extname = 'postgis'")).isEqualTo(1);
		assertThat(colunaExiste("imovel", "geom")).isTrue();
		assertThat(colunaExiste("imovel", "ponto")).isTrue();
	}

	private static Flyway flyway(String alvo) {
		var configuracao = Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration");

		if (alvo != null) {
			configuracao = configuracao.target(alvo);
		}

		return configuracao.load();
	}

	private static void executar(String sql) throws SQLException {
		try (Connection conexao = dataSource.getConnection(); Statement st = conexao.createStatement()) {
			st.execute(sql);
		}
	}

	private static int contar(String sql) throws SQLException {
		try (Connection conexao = dataSource.getConnection();
				Statement st = conexao.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getInt(1);
		}
	}

	private static List<String> consultarTextos(String sql) throws SQLException {
		try (Connection conexao = dataSource.getConnection();
				Statement st = conexao.createStatement();
				ResultSet rs = st.executeQuery(sql)) {

			List<String> valores = new ArrayList<>();
			while (rs.next()) {
				valores.add(rs.getString(1));
			}
			return valores;
		}
	}

	private static boolean colunaExiste(String tabela, String coluna) throws SQLException {
		return contar("""
				SELECT count(*) FROM information_schema.columns
				 WHERE table_name = '%s' AND column_name = '%s'
				""".formatted(tabela, coluna)) > 0;
	}
}
