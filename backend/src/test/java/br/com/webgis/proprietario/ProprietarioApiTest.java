package br.com.webgis.proprietario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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


@AutoConfigureMockMvc
@DisplayName("API de proprietarios")
class ProprietarioApiTest extends IntegracaoBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@BeforeEach
	void limpar() {
		limparCadastro();
	}

	@Test
	@DisplayName("renomear o titular vale para TODOS os imoveis dele (requisito 5)")
	void renomearRefleteEmTodosOsImoveis() throws Exception {
		criarImovel("Maria Aparecida Souza", "Sao Paulo");
		criarImovel("Maria Aparecida Souza", "Campinas");
		criarImovel("Maria Aparecida Souza", "Santos");
		criarImovel("Joao Carlos Ferreira", "Curitiba");

		long idMaria = idDoProprietarioDe("Maria Aparecida Souza");

		mockMvc.perform(put("/api/proprietarios/{id}", idMaria)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Maria Aparecida Souza Ferreira\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Maria Aparecida Souza Ferreira"))
				.andExpect(jsonPath("$.quantidadeImoveis").value(3));

		// Os tres imoveis passam a exibir o nome novo...
		mockMvc.perform(get("/api/imoveis").param("proprietarioId", String.valueOf(idMaria)))
				.andExpect(jsonPath("$.totalDeElementos").value(3))
				.andExpect(jsonPath("$.conteudo[0].proprietarioNome").value("Maria Aparecida Souza Ferreira"))
				.andExpect(jsonPath("$.conteudo[1].proprietarioNome").value("Maria Aparecida Souza Ferreira"))
				.andExpect(jsonPath("$.conteudo[2].proprietarioNome").value("Maria Aparecida Souza Ferreira"));

		// ...e o imovel de outro titular nao foi tocado.
		mockMvc.perform(get("/api/imoveis").param("proprietarioNome", "Joao"))
				.andExpect(jsonPath("$.conteudo[0].proprietarioNome").value("Joao Carlos Ferreira"));
	}

	@Test
	@DisplayName("a listagem traz a contagem de imoveis agregada")
	void listagemComContagem() throws Exception {
		criarImovel("Maria Aparecida Souza", "Sao Paulo");
		criarImovel("Maria Aparecida Souza", "Campinas");
		criarImovel("Joao Carlos Ferreira", "Curitiba");

		mockMvc.perform(get("/api/proprietarios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalDeElementos").value(2))
				.andExpect(jsonPath("$.conteudo[0].nome").value("Joao Carlos Ferreira"))
				.andExpect(jsonPath("$.conteudo[0].quantidadeImoveis").value(1))
				.andExpect(jsonPath("$.conteudo[1].nome").value("Maria Aparecida Souza"))
				.andExpect(jsonPath("$.conteudo[1].quantidadeImoveis").value(2));
	}

	@Test
	@DisplayName("proprietario recem criado aparece com zero imoveis")
	void criarProprietario() throws Exception {
		mockMvc.perform(post("/api/proprietarios")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Fernanda Ribeiro Alves\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nome").value("Fernanda Ribeiro Alves"))
				.andExpect(jsonPath("$.quantidadeImoveis").value(0));
	}

	@Test
	@DisplayName("nome duplicado devolve 409, mesmo com caixa e espacos diferentes")
	void nomeDuplicado() throws Exception {
		mockMvc.perform(post("/api/proprietarios")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Ana Beatriz Lima\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/proprietarios")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"  ANA   Beatriz  LIMA \"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:conflito-de-dados"));
	}

	@Test
	@DisplayName("renomear para um nome ja usado devolve 409")
	void renomearParaNomeExistente() throws Exception {
		criarImovel("Maria Aparecida Souza", "Sao Paulo");
		criarImovel("Joao Carlos Ferreira", "Curitiba");

		long idJoao = idDoProprietarioDe("Joao Carlos Ferreira");

		mockMvc.perform(put("/api/proprietarios/{id}", idJoao)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Maria Aparecida Souza\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("renomear para o proprio nome com outra grafia e permitido")
	void renomearParaMesmoNome() throws Exception {
		criarImovel("Maria Aparecida Souza", "Sao Paulo");
		long id = idDoProprietarioDe("Maria Aparecida Souza");

		mockMvc.perform(put("/api/proprietarios/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"MARIA APARECIDA SOUZA\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("MARIA APARECIDA SOUZA"));
	}

	@Test
	@DisplayName("nao exclui titular que ainda tem imoveis")
	void naoExcluiComImoveis() throws Exception {
		criarImovel("Maria Aparecida Souza", "Sao Paulo");
		long id = idDoProprietarioDe("Maria Aparecida Souza");

		mockMvc.perform(delete("/api/proprietarios/{id}", id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 imovel")));
	}

	@Test
	@DisplayName("proprietario inexistente devolve 404")
	void inexistente() throws Exception {
		mockMvc.perform(get("/api/proprietarios/{id}", 999999))
				.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/proprietarios/{id}", 999999)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Alguem\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("nome em branco devolve 400")
	void nomeEmBranco() throws Exception {
		mockMvc.perform(post("/api/proprietarios")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"   \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("busca por nome parcial")
	void buscaParcial() throws Exception {
		criarImovel("Maria Aparecida Souza", "Sao Paulo");
		criarImovel("Joao Carlos Ferreira", "Curitiba");

		mockMvc.perform(get("/api/proprietarios").param("busca", "carlos"))
				.andExpect(jsonPath("$.totalDeElementos").value(1))
				.andExpect(jsonPath("$.conteudo[0].nome").value("Joao Carlos Ferreira"));
	}

	private void criarImovel(String proprietario, String municipio) throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel(proprietario, municipio, "SP", 100)))
				.andExpect(status().isCreated());
	}

	private long idDoProprietarioDe(String nome) throws Exception {
		MvcResult resultado = mockMvc.perform(get("/api/imoveis").param("proprietarioNome", nome))
				.andExpect(status().isOk())
				.andReturn();

		var conteudo = json.readTree(resultado.getResponse().getContentAsString()).get("conteudo");
		assertThat(conteudo).isNotEmpty();

		return conteudo.get(0).get("proprietarioId").asLong();
	}
}
