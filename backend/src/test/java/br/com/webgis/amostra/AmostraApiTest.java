package br.com.webgis.amostra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import br.com.webgis.IntegracaoBase;

/**
 * Geracao de massa de teste.
 *
 * <p>O que estes testes protegem nao e "inseriu N linhas", e sim que a massa
 * gerada obedece as <b>mesmas</b> regras do cadastro manual: dimensoes em par,
 * area coerente com o poligono e nenhum lote sobre outro. Uma carga que viola a
 * regra de nao sobreposicao encheria o banco de dados que a propria API
 * recusaria criar.
 */
@AutoConfigureMockMvc
@DisplayName("Geracao de imoveis de amostra")
class AmostraApiTest extends IntegracaoBase {

	private static final String BAIRRO = "Distrito Amostra";

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void limpar() {
		limparCadastro();
	}

	@Test
	@DisplayName("cria a quantidade pedida, toda com dimensoes e poligono")
	void criaComDimensoes() throws Exception {
		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "120"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.solicitados").value(120))
				.andExpect(jsonPath("$.criados").value(120))
				.andExpect(jsonPath("$.ignorados").value(0))
				.andExpect(jsonPath("$.municipio").value("Bauru"));

		assertThat(contar("SELECT count(*) FROM imovel WHERE bairro = ?", BAIRRO)).isEqualTo(120);

		assertThat(contar("""
				SELECT count(*) FROM imovel
				 WHERE bairro = ?
				   AND (largura_m IS NULL OR comprimento_m IS NULL OR geom IS NULL)
				""", BAIRRO))
				.as("todo lote de amostra tem dimensoes e poligono")
				.isZero();
	}

	@Test
	@DisplayName("nenhum lote gerado intersecta outro imovel")
	void naoSobrepoe() throws Exception {
		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "200"))
				.andExpect(status().isCreated());

		assertThat(contar("""
				SELECT count(*)
				  FROM imovel a JOIN imovel b ON a.id < b.id
				 WHERE a.geom IS NOT NULL AND b.geom IS NOT NULL
				   AND ST_Intersects(a.geom, b.geom)
				"""))
				.as("a grade e nao sobreposta por construcao")
				.isZero();
	}

	@Test
	@DisplayName("area gravada bate com as dimensoes e com o poligono do PostGIS")
	void areaCoerente() throws Exception {
		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "80"))
				.andExpect(status().isCreated());

		assertThat(contar("""
				SELECT count(*) FROM imovel
				 WHERE bairro = ?
				   AND (abs(area_m2 - largura_m * comprimento_m) > 0.01
				        OR abs(ST_Area(geom) - largura_m * comprimento_m) > 0.5)
				""", BAIRRO))
				.isZero();

		// O ponto do imovel e o centro do lote: o centroide do poligono gravado
		// tem que cair sobre a latitude/longitude das colunas.
		assertThat(consultarDouble("""
				SELECT coalesce(max(abs(ST_Y(ST_Transform(ST_Centroid(geom), 4326)) - latitude)), 0)
				  FROM imovel WHERE bairro = ?
				""", BAIRRO))
				.isLessThan(0.0000001);
	}

	@Test
	@DisplayName("chamadas sucessivas empilham blocos novos, sem colidir com os anteriores")
	void blocosSucessivos() throws Exception {
		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "60"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.criados").value(60));

		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "60"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.criados").value(60));

		assertThat(contar("SELECT count(*) FROM imovel WHERE bairro = ?", BAIRRO)).isEqualTo(120);

		assertThat(contar("""
				SELECT count(*)
				  FROM imovel a JOIN imovel b ON a.id < b.id
				 WHERE a.geom IS NOT NULL AND b.geom IS NOT NULL
				   AND ST_Intersects(a.geom, b.geom)
				"""))
				.isZero();
	}

	@Test
	@DisplayName("distribui os lotes entre varios titulares, reaproveitando os existentes")
	void distribuiEntreTitulares() throws Exception {
		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "100"))
				.andExpect(status().isCreated());

		long titularesUsados = contar(
				"SELECT count(DISTINCT proprietario_id) FROM imovel WHERE bairro = ?", BAIRRO);

		assertThat(titularesUsados).isGreaterThan(1);

		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "100"))
				.andExpect(status().isCreated());

		assertThat(contar("SELECT count(*) FROM proprietario WHERE nome LIKE 'Titular de Amostra %'"))
				.as("a segunda carga reaproveita os titulares, nao duplica")
				.isEqualTo(titularesUsados);
	}

	@Test
	@DisplayName("quantidade acima do teto configurado e reduzida, nao recusada")
	void respeitaTeto() throws Exception {
		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "999999"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.solicitados").value(5000));
	}

	@Test
	@DisplayName("quantidade invalida vira 400")
	void quantidadeInvalida() throws Exception {
		mockMvc.perform(post("/api/amostra/imoveis").param("quantidade", "0"))
				.andExpect(status().isBadRequest());
	}
}
