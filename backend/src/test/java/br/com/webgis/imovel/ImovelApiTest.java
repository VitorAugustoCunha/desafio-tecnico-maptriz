package br.com.webgis.imovel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
 * Contrato HTTP do CRUD de imoveis.
 *
 * <p>Boa parte dos casos aqui e a contraprova direta das evidencias registradas
 * em docs/CODE_REVIEW.md: o que o baseline respondia errado, agora responde
 * certo.
 */
@AutoConfigureMockMvc
@DisplayName("API de imoveis")
class ImovelApiTest extends IntegracaoBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@BeforeEach
	void limpar() {
		limparCadastro();
	}

	@Test
	@DisplayName("CRUD completo: cria com 201 + Location, le, atualiza, exclui com 204")
	void crudCompleto() throws Exception {
		MvcResult criacao = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel("Maria Aparecida Souza", "Sao Paulo", "SP", 320.50)))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.proprietario.nome").value("Maria Aparecida Souza"))
				.andExpect(jsonPath("$.municipio").value("Sao Paulo"))
				.andExpect(jsonPath("$.possuiGeometria").value(false))
				.andReturn();

		long id = json.readTree(criacao.getResponse().getContentAsString()).get("id").asLong();

		assertThat(criacao.getResponse().getHeader("Location")).endsWith("/api/imoveis/" + id);

		mockMvc.perform(get("/api/imoveis/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.areaM2").value(320.50));

		mockMvc.perform(put("/api/imoveis/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel("Maria Aparecida Souza", "Campinas", "SP", 400.00)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.municipio").value("Campinas"))
				.andExpect(jsonPath("$.areaM2").value(400.00));

		mockMvc.perform(delete("/api/imoveis/{id}", id))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/imoveis/{id}", id))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("GET de id inexistente devolve 404 com ProblemDetail (baseline: 200 vazio)")
	void buscarInexistente() throws Exception {
		mockMvc.perform(get("/api/imoveis/{id}", 999999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:nao-encontrado"))
				.andExpect(jsonPath("$.title").value("Recurso nao encontrado"))
				.andExpect(jsonPath("$.recurso").value("Imovel"));
	}

	@Test
	@DisplayName("DELETE de id inexistente devolve 404 (baseline: 200 {\"status\":\"ok\"})")
	void excluirInexistente() throws Exception {
		mockMvc.perform(delete("/api/imoveis/{id}", 999999))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("id nao numerico devolve 400 (baseline: virava injecao de SQL)")
	void idNaoNumerico() throws Exception {
		mockMvc.perform(get("/api/imoveis/{id}", "0 or 1=1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:validacao"));
	}

	@Test
	@DisplayName("nome com apostrofo e gravado normalmente (baseline: 500 por SQL quebrada)")
	void nomeComApostrofo() throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel("Vitor O'Brien D'Avila", "Curitiba", "PR", 100.00)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.proprietario.nome").value("Vitor O'Brien D'Avila"));
	}

	@Test
	@DisplayName("payload invalido devolve 400 com a lista de erros por campo")
	void payloadInvalido() throws Exception {
		String invalido = """
				{"proprietarioNome":"","municipio":"","uf":"XXXXX","bairro":"","rua":"","numero":"",
				 "latitude":999,"longitude":-999,"areaM2":-50,"ativo":true}
				""";

		MvcResult resultado = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalido))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:validacao"))
				.andExpect(jsonPath("$.erros").isArray())
				.andReturn();

		JsonNode erros = json.readTree(resultado.getResponse().getContentAsString()).get("erros");

		List<String> camposComErro = new ArrayList<>();
		erros.forEach(erro -> camposComErro.add(erro.get("campo").asText()));

		assertThat(camposComErro).contains("latitude", "longitude", "uf", "municipio", "areaM2");
	}

	@Test
	@DisplayName("corpo que nao e objeto JSON devolve 400 (baseline: 200 com corpo vazio)")
	void corpoNaoObjeto() throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content("[1,2,3]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:corpo-invalido"));
	}

	@Test
	@DisplayName("PUT com coordenada nula e recusado (baseline: 200 e apagava a coordenada)")
	void putComCoordenadaNulaNaoApagaDado() throws Exception {
		MvcResult criacao = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel("Maria Souza", "Sao Paulo", "SP", 100.00)))
				.andExpect(status().isCreated())
				.andReturn();

		long id = json.readTree(criacao.getResponse().getContentAsString()).get("id").asLong();

		String semCoordenadas = """
				{"proprietarioNome":"Maria Souza","municipio":"Sao Paulo","uf":"SP","bairro":"Centro",
				 "rua":"Rua A","numero":"10","latitude":null,"longitude":null,"areaM2":null,"ativo":true}
				""";

		mockMvc.perform(put("/api/imoveis/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(semCoordenadas))
				.andExpect(status().isBadRequest());

		// O dado gravado continua intacto.
		MvcResult depois = mockMvc.perform(get("/api/imoveis/{id}", id))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode imovel = json.readTree(depois.getResponse().getContentAsString());

		assertThat(new BigDecimal(imovel.get("latitude").asText())).isEqualByComparingTo("-23.5629");
		assertThat(new BigDecimal(imovel.get("longitude").asText())).isEqualByComparingTo("-46.6944");
		assertThat(new BigDecimal(imovel.get("areaM2").asText())).isEqualByComparingTo("100.00");
	}

	@Test
	@DisplayName("PUT sem um campo de texto e recusado (baseline: gravava a string 'null')")
	void putSemCampoDeTexto() throws Exception {
		MvcResult criacao = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel("Maria Souza", "Sao Paulo", "SP", 100.00)))
				.andReturn();

		long id = json.readTree(criacao.getResponse().getContentAsString()).get("id").asLong();

		String semBairro = """
				{"proprietarioNome":"Maria Souza","municipio":"Sao Paulo","uf":"SP",
				 "rua":"Rua A","numero":"10","latitude":-23.5629,"longitude":-46.6944,
				 "areaM2":100,"ativo":true}
				""";

		mockMvc.perform(put("/api/imoveis/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(semBairro))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/imoveis/{id}", id))
				.andExpect(jsonPath("$.bairro").value("Pinheiros"));
	}

	@Test
	@DisplayName("UF minuscula e normalizada para maiuscula")
	void normalizaUf() throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel("Maria Souza", "Sao Paulo", "sp", 100.00)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.uf").value("SP"));
	}

	@Test
	@DisplayName("dois imoveis com o mesmo nome de titular apontam para o mesmo proprietario")
	void reaproveitaProprietario() throws Exception {
		MvcResult primeiro = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel("Maria Aparecida Souza", "Sao Paulo", "SP", 100.00)))
				.andReturn();

		MvcResult segundo = mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel("  MARIA   Aparecida  SOUZA ", "Campinas", "SP", 200.00)))
				.andReturn();

		long idA = json.readTree(primeiro.getResponse().getContentAsString()).get("proprietario").get("id").asLong();
		long idB = json.readTree(segundo.getResponse().getContentAsString()).get("proprietario").get("id").asLong();

		assertThat(idB).as("variacao de caixa e espaco resolve para o mesmo titular").isEqualTo(idA);
	}

}
