package br.com.webgis.gis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.webgis.CorposDeTeste;
import br.com.webgis.IntegracaoBase;

/**
 * Lote desenhado no mapa — a forma livre, alternativa a largura x comprimento.
 *
 * <p>Coordenadas em Curitiba, dentro da zona UTM 22S do EPSG:31982.
 */
@AutoConfigureMockMvc
@DisplayName("Poligono desenhado no mapa")
class PoligonoDesenhadoApiTest extends IntegracaoBase {

	private static final BigDecimal LATITUDE = new BigDecimal("-25.4420");
	private static final BigDecimal LONGITUDE = new BigDecimal("-49.2920");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@BeforeEach
	void limpar() {
		limparCadastro();
	}

	@Test
	@DisplayName("aceita um lote desenhado e grava POLYGON em SRID 31982")
	void desenhoValido() throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Maria Souza", QUADRADO)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.possuiGeometria").value(true))
				// Lote desenhado nao tem "largura" e "comprimento" unicos.
				.andExpect(jsonPath("$.larguraM").doesNotExist())
				.andExpect(jsonPath("$.comprimentoM").doesNotExist())
				.andReturn();

		long id = json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();

		assertThat(consultarTexto("SELECT ST_GeometryType(geom) FROM imovel WHERE id = ?", id))
				.isEqualTo("ST_Polygon");
		assertThat(contar("SELECT ST_SRID(geom) FROM imovel WHERE id = ?", id)).isEqualTo(31982);
		assertThat(consultarBooleano("SELECT ST_IsValid(geom) FROM imovel WHERE id = ?", id)).isTrue();
	}

	@Test
	@DisplayName("a area gravada e derivada do desenho, nao do que o cliente enviou")
	void areaDerivadaDoDesenho() throws Exception {
		// O corpo manda areaM2 = 999999; o poligono real tem ~0,0011 grau de lado.
		String corpo = """
				{"proprietarioNome":"Maria Souza","municipio":"Curitiba","uf":"PR","bairro":"Batel",
				 "rua":"Av do Batel","numero":"1560","latitude":%s,"longitude":%s,
				 "areaM2":999999,"geometria":%s,"ativo":true}
				""".formatted(LATITUDE, LONGITUDE, QUADRADO);

		MvcResult resultado = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isCreated())
				.andReturn();

		long id = json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();

		double areaGravada = consultarDouble("SELECT area_m2 FROM imovel WHERE id = ?", id);
		double areaDaGeometria = consultarDouble("SELECT ST_Area(geom) FROM imovel WHERE id = ?", id);

		assertThat(areaGravada)
				.as("a area enviada pelo cliente e ignorada: quem manda e o poligono")
				.isNotEqualTo(999999.0)
				.isCloseTo(areaDaGeometria, Offset.offset(0.01));
	}

	@Test
	@DisplayName("o ponto do imovel passa a ser o centroide do desenho")
	void pontoVemDoCentroide() throws Exception {
		// Manda um ponto propositalmente longe do poligono desenhado.
		String corpo = """
				{"proprietarioNome":"Maria Souza","municipio":"Curitiba","uf":"PR","bairro":"Batel",
				 "rua":"Av do Batel","numero":"1560","latitude":-10.0,"longitude":-40.0,
				 "geometria":%s,"ativo":true}
				""".formatted(QUADRADO);

		MvcResult resultado = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isCreated())
				.andReturn();

		long id = json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();

		double distancia = consultarDouble("""
				SELECT ST_Distance(ST_Centroid(geom), ST_Transform(ponto, 31982))
				  FROM imovel WHERE id = ?
				""", id);

		assertThat(distancia)
				.as("o ponto enviado foi substituido pelo centroide do poligono")
				.isCloseTo(0.0, Offset.offset(0.5));
	}

	@Test
	@DisplayName("recusa poligono com auto-interseccao (o erro classico de quem desenha a mao)")
	void recusaAutoInterseccao() throws Exception {
		// Formato "gravata": as bordas se cruzam, entao o poligono e invalido.
		String gravata = """
				{"type":"Polygon","coordinates":[[
				  [-49.2925,-25.4425],[-49.2915,-25.4415],
				  [-49.2925,-25.4415],[-49.2915,-25.4425],
				  [-49.2925,-25.4425]]]}
				""";

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Maria Souza", gravata)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("invalido")));

		assertThat(contar("SELECT count(*) FROM imovel")).isZero();
	}

	@Test
	@DisplayName("recusa anel aberto ou com poucos pontos antes de chegar ao banco")
	void recusaAnelMalformado() throws Exception {
		String aberto = """
				{"type":"Polygon","coordinates":[[
				  [-49.2925,-25.4425],[-49.2915,-25.4425],[-49.2915,-25.4415]]]}
				""";

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Maria Souza", aberto)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:validacao"));
	}

	@Test
	@DisplayName("recusa geometria que nao e Polygon")
	void recusaTipoErrado() throws Exception {
		String ponto = "{\"type\":\"Point\",\"coordinates\":[[[-49.29,-25.44]]]}";

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Maria Souza", ponto)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("recusa coordenada fora da faixa valida")
	void recusaCoordenadaForaDaFaixa() throws Exception {
		String fora = """
				{"type":"Polygon","coordinates":[[
				  [-999,-25.4425],[-49.2915,-25.4425],[-49.2915,-25.4415],[-999,-25.4425]]]}
				""";

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Maria Souza", fora)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("desenho e dimensoes juntos sao recusados: as formas sao excludentes")
	void recusaAsDuasFormasJuntas() throws Exception {
		String corpo = """
				{"proprietarioNome":"Maria Souza","municipio":"Curitiba","uf":"PR","bairro":"Batel",
				 "rua":"Av do Batel","numero":"1560","latitude":%s,"longitude":%s,
				 "larguraM":20,"comprimentoM":50,"geometria":%s,"ativo":true}
				""".formatted(LATITUDE, LONGITUDE, QUADRADO);

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[*].campo")
						.value(org.hamcrest.Matchers.hasItem("formaUnica")));
	}

	@Test
	@DisplayName("desenho que invade um retangulo ja cadastrado devolve 409")
	void desenhoConflitaComRetangulo() throws Exception {
		// Primeiro um lote pelo caminho do enunciado: centro + dimensoes.
		MvcResult retangulo = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Dono do Retangulo", LATITUDE, LONGITUDE, 60, 60)))
				.andExpect(status().isCreated())
				.andReturn();

		long idRetangulo = json.readTree(retangulo.getResponse().getContentAsString()).get("id").asLong();

		// Agora um desenho por cima. As duas formas compartilham a mesma regra.
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Invasor", QUADRADO)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:conflito-espacial"))
				.andExpect(jsonPath("$.idImovelConflitante").value(idRetangulo));

		assertThat(contar("SELECT count(*) FROM imovel"))
				.as("o desenho recusado nao pode ficar gravado")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("retangulo que invade um desenho ja cadastrado tambem devolve 409")
	void retanguloConflitaComDesenho() throws Exception {
		MvcResult desenho = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Dono do Desenho", QUADRADO)))
				.andExpect(status().isCreated())
				.andReturn();

		long idDesenho = json.readTree(desenho.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Invasor", LATITUDE, LONGITUDE, 60, 60)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.idImovelConflitante").value(idDesenho));
	}

	@Test
	@DisplayName("desenho em area livre e aceito")
	void desenhoEmAreaLivre() throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Primeiro", QUADRADO)))
				.andExpect(status().isCreated());

		// ~1 km ao norte.
		String longe = """
				{"type":"Polygon","coordinates":[[
				  [-49.2925,-25.4325],[-49.2915,-25.4325],
				  [-49.2915,-25.4315],[-49.2925,-25.4315],
				  [-49.2925,-25.4325]]]}
				""";

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Vizinho", longe)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.possuiGeometria").value(true));
	}

	@Test
	@DisplayName("o detalhe devolve a geometria para a edicao redesenhar o lote")
	void detalheDevolveGeometria() throws Exception {
		MvcResult criacao = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Maria Souza", QUADRADO)))
				.andReturn();

		long id = json.readTree(criacao.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(get("/api/imoveis/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.geometria.type").value("Polygon"))
				.andExpect(jsonPath("$.geometria.coordinates[0]").isArray());
	}

	@Test
	@DisplayName("a listagem NAO carrega geometria")
	void listagemSemGeometria() throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Maria Souza", QUADRADO)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/imoveis"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo[0].geometria").doesNotExist());
	}

	@Test
	@DisplayName("trocar o desenho por dimensoes substitui a geometria")
	void trocarDesenhoPorDimensoes() throws Exception {
		MvcResult criacao = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(comDesenho("Maria Souza", QUADRADO)))
				.andReturn();

		long id = json.readTree(criacao.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(put("/api/imoveis/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 20, 50)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.larguraM").value(20))
				.andExpect(jsonPath("$.possuiGeometria").value(true));

		assertThat(consultarDouble("SELECT ST_Area(geom) FROM imovel WHERE id = ?", id))
				.as("agora a geometria e o retangulo de 20 x 50")
				.isCloseTo(1000.0, Offset.offset(0.5));
	}

	/** Quadrado de ~0,001 grau de lado (~100 m), livre por padrao nos testes. */
	private static final String QUADRADO = """
			{"type":"Polygon","coordinates":[[
			  [-49.2925,-25.4425],[-49.2915,-25.4425],
			  [-49.2915,-25.4415],[-49.2925,-25.4415],
			  [-49.2925,-25.4425]]]}
			""";

	private static String comDesenho(String proprietario, String geometria) {
		return """
				{"proprietarioNome":"%s","municipio":"Curitiba","uf":"PR","bairro":"Batel",
				 "rua":"Av do Batel","numero":"1560","latitude":%s,"longitude":%s,
				 "geometria":%s,"ativo":true}
				""".formatted(proprietario, LATITUDE, LONGITUDE, geometria);
	}
}
