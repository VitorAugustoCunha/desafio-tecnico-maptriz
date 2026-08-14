package br.com.webgis.gis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.webgis.CorposDeTeste;
import br.com.webgis.IntegracaoBase;

/**
 * Consulta espacial por viewport (tarefa 7).
 *
 * <p>Curitiba e Sao Paulo ficam bem separadas, entao um viewport em cima de uma
 * nao pode trazer a outra.
 */
@AutoConfigureMockMvc
@DisplayName("Mapa por viewport")
class MapaApiTest extends IntegracaoBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@BeforeEach
	void prepararCadastro() throws Exception {
		limparCadastro();

		// Curitiba
		criarComDimensoes("Roberto Melo", new BigDecimal("-25.4420"), new BigDecimal("-49.2920"));
		criarSemDimensoes("Ana Curitiba", new BigDecimal("-25.4500"), new BigDecimal("-49.3000"));

		// Sao Paulo
		criarSemDimensoes("Maria Souza", new BigDecimal("-23.5629"), new BigDecimal("-46.6944"));
		criarSemDimensoes("Joao Ferreira", new BigDecimal("-23.5010"), new BigDecimal("-46.6280"));
	}

	@Test
	@DisplayName("traz apenas os imoveis dentro do bbox")
	void recortaPeloViewport() throws Exception {
		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-49.40").param("minLat", "-25.55")
				.param("maxLon", "-49.20").param("maxLat", "-25.35")
				.param("zoom", "14"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("FeatureCollection"))
				.andExpect(jsonPath("$.features.length()").value(2))
				.andExpect(jsonPath("$.metadados.agregado").value(false));
	}

	@Test
	@DisplayName("viewport sobre Sao Paulo nao traz os imoveis de Curitiba")
	void naoVazaOutraRegiao() throws Exception {
		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-46.80").param("minLat", "-23.65")
				.param("maxLon", "-46.55").param("maxLat", "-23.45")
				.param("zoom", "14"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.features.length()").value(2))
				.andExpect(jsonPath("$.features[0].properties.municipio").value("Sao Paulo"));
	}

	@Test
	@DisplayName("viewport vazio devolve colecao vazia, nao erro")
	void viewportVazio() throws Exception {
		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-10").param("minLat", "10")
				.param("maxLon", "-9").param("maxLat", "11")
				.param("zoom", "14"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.features.length()").value(0))
				.andExpect(jsonPath("$.metadados.total").value(0));
	}

	@Test
	@DisplayName("imovel com dimensoes vem como Polygon; imovel legado vem como Point")
	void poligonoEPonto() throws Exception {
		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-49.40").param("minLat", "-25.55")
				.param("maxLon", "-49.20").param("maxLat", "-25.35")
				.param("zoom", "16"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
				.andExpect(jsonPath("$.features[0].properties.poligono").value(true))
				.andExpect(jsonPath("$.features[1].geometry.type").value("Point"))
				.andExpect(jsonPath("$.features[1].properties.poligono").value(false));
	}

	@Test
	@DisplayName("o poligono do mapa vem reprojetado para 4326, nao em metros de 31982")
	void poligonoEmGraus() throws Exception {
		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-49.40").param("minLat", "-25.55")
				.param("maxLon", "-49.20").param("maxLat", "-25.35")
				.param("zoom", "16"))
				.andExpect(status().isOk())
				// Primeira coordenada do anel externo: longitude proxima de -49.29.
				.andExpect(jsonPath("$.features[0].geometry.coordinates[0][0][0]")
						.value(org.hamcrest.Matchers.closeTo(-49.292, 0.01)))
				.andExpect(jsonPath("$.features[0].geometry.coordinates[0][0][1]")
						.value(org.hamcrest.Matchers.closeTo(-25.442, 0.01)));
	}

	@Test
	@DisplayName("em zoom afastado o servidor agrega no PostGIS em vez de mandar feicao por feicao")
	void agregaEmZoomAfastado() throws Exception {
		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-55").param("minLat", "-30")
				.param("maxLon", "-40").param("maxLat", "-20")
				.param("zoom", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.metadados.agregado").value(true))
				.andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
				.andExpect(jsonPath("$.features[0].properties.quantidade").isNumber());
	}

	@Test
	@DisplayName("a agregacao nao perde imovel: a soma das quantidades bate com o total")
	void agregacaoSomaTudo() throws Exception {
		MvcResult resultado = mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-55").param("minLat", "-30")
				.param("maxLon", "-40").param("maxLat", "-20")
				.param("zoom", "4"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.metadados.agregado").value(true))
				.andReturn();

		JsonNode feicoes = json.readTree(resultado.getResponse().getContentAsString()).get("features");

		long soma = 0;
		for (JsonNode feicao : feicoes) {
			soma += feicao.get("properties").get("quantidade").asLong();
		}

		assertThat(soma)
				.as("clusterizar nao pode fazer imovel sumir da contagem")
				.isEqualTo(4);
	}

	@Test
	@DisplayName("bbox invertido pelo arrasto do mapa e normalizado em vez de recusado")
	void bboxInvertido() throws Exception {
		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-49.20").param("minLat", "-25.35")
				.param("maxLon", "-49.40").param("maxLat", "-25.55")
				.param("zoom", "14"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.features.length()").value(2));
	}

	@Test
	@DisplayName("coordenada fora da faixa valida devolve 400")
	void coordenadaInvalida() throws Exception {
		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-999").param("minLat", "-25.55")
				.param("maxLon", "-49.20").param("maxLat", "-25.35")
				.param("zoom", "14"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("imovel inativo fica de fora por padrao")
	void filtraInativos() throws Exception {
		jdbc.update("UPDATE imovel SET ativo = false WHERE municipio = 'Curitiba'");

		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-49.40").param("minLat", "-25.55")
				.param("maxLon", "-49.20").param("maxLat", "-25.35")
				.param("zoom", "14"))
				.andExpect(jsonPath("$.features.length()").value(0));

		mockMvc.perform(get("/api/mapa/imoveis")
				.param("minLon", "-49.40").param("minLat", "-25.55")
				.param("maxLon", "-49.20").param("maxLat", "-25.35")
				.param("zoom", "14")
				.param("apenasAtivos", "false"))
				.andExpect(jsonPath("$.features.length()").value(2));
	}

	private void criarComDimensoes(String proprietario, BigDecimal latitude, BigDecimal longitude) throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes(proprietario, latitude, longitude, 20, 50)))
				.andExpect(status().isCreated());
	}

	private void criarSemDimensoes(String proprietario, BigDecimal latitude, BigDecimal longitude) throws Exception {
		boolean curitiba = latitude.doubleValue() < -25;

		String corpo = """
				{"proprietarioNome":"%s","municipio":"%s","uf":"%s","bairro":"Centro",
				 "rua":"Rua Central","numero":"100","latitude":%s,"longitude":%s,
				 "areaM2":100,"ativo":true}
				""".formatted(proprietario,
				curitiba ? "Curitiba" : "Sao Paulo",
				curitiba ? "PR" : "SP",
				latitude, longitude);

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpo))
				.andExpect(status().isCreated());
	}
}
