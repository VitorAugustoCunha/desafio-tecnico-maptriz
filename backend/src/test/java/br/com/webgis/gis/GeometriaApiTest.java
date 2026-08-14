package br.com.webgis.gis;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.webgis.CorposDeTeste;
import br.com.webgis.IntegracaoBase;

/**
 * Geometria real e regra de nao sobreposicao (tarefa 8).
 *
 * <p>Coordenadas de Curitiba, dentro da zona UTM 22S coberta pelo EPSG:31982.
 */
@AutoConfigureMockMvc
@DisplayName("Geometria do imovel e nao sobreposicao")
class GeometriaApiTest extends IntegracaoBase {

	private static final BigDecimal LATITUDE = new BigDecimal("-25.4420");
	private static final BigDecimal LONGITUDE = new BigDecimal("-49.2920");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private GeometriaRepository geometrias;

	@BeforeEach
	void limpar() {
		limparCadastro();
	}

	@Test
	@DisplayName("gera POLYGON valido em SRID 31982 com a area das dimensoes")
	void geraPoligonoNoSridCorreto() throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 20, 50)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.possuiGeometria").value(true))
				.andExpect(jsonPath("$.larguraM").value(20))
				.andExpect(jsonPath("$.comprimentoM").value(50))
				.andReturn();

		long id = json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();

		assertThat(consultarTexto("SELECT ST_GeometryType(geom) FROM imovel WHERE id = ?", id))
				.isEqualTo("ST_Polygon");

		assertThat(contar("SELECT ST_SRID(geom) FROM imovel WHERE id = ?", id))
				.as("o enunciado fixa o SRID 31982")
				.isEqualTo(31982);

		assertThat(consultarBooleano("SELECT ST_IsValid(geom) FROM imovel WHERE id = ?", id)).isTrue();

		// 20 m x 50 m = 1000 m², medidos no plano projetado (metros).
		assertThat(consultarDouble("SELECT ST_Area(geom) FROM imovel WHERE id = ?", id))
				.isCloseTo(1000.0, Offset.offset(0.5));

		// A area gravada acompanha o poligono.
		assertThat(consultarDouble("SELECT area_m2 FROM imovel WHERE id = ?", id))
				.isCloseTo(1000.0, Offset.offset(0.01));
	}

	@Test
	@DisplayName("o ponto informado fica no centro do retangulo")
	void pontoNoCentroDoRetangulo() throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 20, 50)))
				.andReturn();

		long id = json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();

		// A distancia entre o centroide do poligono e o ponto informado precisa ser
		// praticamente zero — e a convencao documentada (lat/lon = centro do lote).
		double distancia = consultarDouble("""
				SELECT ST_Distance(ST_Centroid(geom), ST_Transform(ponto, 31982))
				  FROM imovel WHERE id = ?
				""", id);

		assertThat(distancia).isCloseTo(0.0, Offset.offset(0.01));
	}

	@Test
	@DisplayName("recusa com 409 um imovel que sobrepoe outro, informando o id conflitante")
	void recusaSobreposicao() throws Exception {
		MvcResult primeiro = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 20, 50)))
				.andExpect(status().isCreated())
				.andReturn();

		long idExistente = json.readTree(primeiro.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Joao Ferreira", LATITUDE, LONGITUDE, 30, 30)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:conflito-espacial"))
				.andExpect(jsonPath("$.idImovelConflitante").value(idExistente))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("conflita")));
	}

	@Test
	@DisplayName("a insercao recusada nao deixa rastro no banco")
	void insercaoRecusadaFazRollback() throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 20, 50)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Joao Ferreira", LATITUDE, LONGITUDE, 30, 30)))
				.andExpect(status().isConflict());

		assertThat(contar("SELECT count(*) FROM imovel"))
				.as("o imovel recusado nao pode ficar gravado")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("aceita imovel separado do primeiro")
	void aceitaImovelSeparado() throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 20, 50)))
				.andExpect(status().isCreated());

		// ~1 km ao norte: sem chance de encostar.
		BigDecimal outraLatitude = LATITUDE.add(new BigDecimal("0.0100"));

		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Joao Ferreira", outraLatitude, LONGITUDE, 20, 50)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.possuiGeometria").value(true));
	}

	@Test
	@DisplayName("encostar a borda conta como conflito: o enunciado diz 'intersecta ou sobrepoe'")
	void bordaEncostadaEhConflito() {
		// Feito em metros, no plano projetado, para nao depender de converter
		// deslocamento em graus: o retangulo transladado exatamente pela sua
		// largura fica encostado no original, sem area em comum.
		boolean encostado = consultarBooleano("""
				SELECT ST_Intersects(
				         webgis_retangulo(%s, %s, 20, 50),
				         ST_Translate(webgis_retangulo(%s, %s, 20, 50), 20, 0))
				""".formatted(LONGITUDE, LATITUDE, LONGITUDE, LATITUDE));

		assertThat(encostado)
				.as("ST_Intersects e verdadeiro para bordas que se tocam — por isso ele, e nao ST_Overlaps")
				.isTrue();

		// Um metro a mais e o conflito acaba.
		boolean separado = consultarBooleano("""
				SELECT ST_Intersects(
				         webgis_retangulo(%s, %s, 20, 50),
				         ST_Translate(webgis_retangulo(%s, %s, 20, 50), 21, 0))
				""".formatted(LONGITUDE, LATITUDE, LONGITUDE, LATITUDE));

		assertThat(separado).isFalse();
	}

	@Test
	@DisplayName("imovel legado sem geometria nao bloqueia o espaco")
	void legadoSemGeometriaNaoBloqueia() throws Exception {
		// Sem dimensoes: fica so com o ponto, geom NULL.
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelSemDimensoes("Maria Souza", LATITUDE, LONGITUDE)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.possuiGeometria").value(false));

		// Mesmo lugar, agora com poligono: nao ha com o que conflitar.
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Joao Ferreira", LATITUDE, LONGITUDE, 20, 50)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.possuiGeometria").value(true));
	}

	@Test
	@DisplayName("editar o proprio imovel no mesmo lugar nao acusa conflito consigo mesmo")
	void edicaoNaoConflitaConsigoMesma() throws Exception {
		MvcResult criacao = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 20, 50)))
				.andExpect(status().isCreated())
				.andReturn();

		long id = json.readTree(criacao.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(put("/api/imoveis/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 25, 40)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.possuiGeometria").value(true));

		assertThat(consultarDouble("SELECT ST_Area(geom) FROM imovel WHERE id = ?", id))
				.isCloseTo(1000.0, Offset.offset(0.5));
	}

	@Test
	@DisplayName("remover as dimensoes apaga o poligono e libera a area")
	void removerDimensoesLiberaArea() throws Exception {
		MvcResult criacao = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Maria Souza", LATITUDE, LONGITUDE, 20, 50)))
				.andReturn();

		long id = json.readTree(criacao.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(put("/api/imoveis/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelSemDimensoes("Maria Souza", LATITUDE, LONGITUDE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.possuiGeometria").value(false));

		// Com o poligono removido, outro imovel pode ocupar o lugar.
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovelComDimensoes("Joao Ferreira", LATITUDE, LONGITUDE, 20, 50)))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("a area calculada pelo PostGIS bate com largura x comprimento")
	@Transactional
	void areaDoRetangulo() {
		double area = geometrias.areaDoRetangulo(LONGITUDE, LATITUDE,
				new BigDecimal("12.5"), new BigDecimal("40"));

		assertThat(area).isCloseTo(500.0, Offset.offset(0.5));
	}


}
