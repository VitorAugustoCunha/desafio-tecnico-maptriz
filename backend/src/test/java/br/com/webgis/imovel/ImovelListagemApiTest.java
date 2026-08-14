package br.com.webgis.imovel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.webgis.CorposDeTeste;
import br.com.webgis.IntegracaoBase;

/**
 * Filtros, paginacao e ordenacao — tudo resolvido no servidor (tarefas 2 e 6).
 */
@AutoConfigureMockMvc
@DisplayName("Listagem de imoveis")
class ImovelListagemApiTest extends IntegracaoBase {

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void prepararCadastro() throws Exception {
		limparCadastro();

		criar("Maria Aparecida Souza", "Sao Paulo", "SP", 100);
		criar("Maria Aparecida Souza", "Campinas", "SP", 200);
		criar("Joao Carlos Ferreira", "Sao Paulo", "SP", 300);
		criar("Ana Beatriz Lima", "Rio de Janeiro", "RJ", 400);
		criar("Carlos Eduardo Nunes", "Curitiba", "PR", 500);
	}

	@Test
	@DisplayName("pagina com o tamanho pedido e informa os totais")
	void paginacao() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("tamanho", "2").param("pagina", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo.length()").value(2))
				.andExpect(jsonPath("$.totalDeElementos").value(5))
				.andExpect(jsonPath("$.totalDePaginas").value(3))
				.andExpect(jsonPath("$.primeira").value(true))
				.andExpect(jsonPath("$.ultima").value(false));

		mockMvc.perform(get("/api/imoveis").param("tamanho", "2").param("pagina", "2"))
				.andExpect(jsonPath("$.conteudo.length()").value(1))
				.andExpect(jsonPath("$.ultima").value(true));
	}

	@Test
	@DisplayName("o tamanho de pagina e cortado no teto configurado")
	void tamanhoMaximo() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("tamanho", "100000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tamanho").value(100));
	}

	@Test
	@DisplayName("filtra por municipio, parcial e sem diferenciar caixa")
	void filtroPorMunicipio() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("municipio", "sao pau"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalDeElementos").value(2));

		mockMvc.perform(get("/api/imoveis").param("municipio", "CURITIBA"))
				.andExpect(jsonPath("$.totalDeElementos").value(1));
	}

	@Test
	@DisplayName("filtra por nome do proprietario, parcial")
	void filtroPorProprietario() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("proprietarioNome", "maria"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalDeElementos").value(2));
	}

	@Test
	@DisplayName("combina os dois filtros")
	void filtrosCombinados() throws Exception {
		mockMvc.perform(get("/api/imoveis")
				.param("proprietarioNome", "maria")
				.param("municipio", "campinas"))
				.andExpect(jsonPath("$.totalDeElementos").value(1))
				.andExpect(jsonPath("$.conteudo[0].municipio").value("Campinas"));
	}

	@Test
	@DisplayName("filtro sem resultado devolve pagina vazia, nao erro")
	void filtroSemResultado() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("municipio", "Municipio Inexistente"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo.length()").value(0))
				.andExpect(jsonPath("$.totalDeElementos").value(0));
	}

	@Test
	@DisplayName("caractere curinga do LIKE e tratado como texto literal")
	void escapaCuringa() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("municipio", "%"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalDeElementos").value(0));
	}

	@Test
	@DisplayName("ordena pelos campos da whitelist")
	void ordenacao() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("ordenarPor", "area").param("direcao", "desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo[0].areaM2").value(500.00));

		mockMvc.perform(get("/api/imoveis").param("ordenarPor", "municipio").param("direcao", "asc"))
				.andExpect(jsonPath("$.conteudo[0].municipio").value("Campinas"));

		mockMvc.perform(get("/api/imoveis").param("ordenarPor", "proprietario").param("direcao", "asc"))
				.andExpect(jsonPath("$.conteudo[0].proprietarioNome").value("Ana Beatriz Lima"));
	}

	@Test
	@DisplayName("ordenacao fora da whitelist devolve 400, nao ordena por campo arbitrario")
	void ordenacaoForaDaWhitelist() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("ordenarPor", "criado_em; drop table imovel"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:webgis:problema:validacao"));
	}

	@Test
	@DisplayName("direcao invalida devolve 400")
	void direcaoInvalida() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("direcao", "lateral"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("pagina negativa devolve 400")
	void paginaNegativa() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("pagina", "-1"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("a listagem traz o nome do titular sem consulta por linha")
	void trazNomeDoProprietario() throws Exception {
		mockMvc.perform(get("/api/imoveis").param("ordenarPor", "id"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo[0].proprietarioNome").value("Maria Aparecida Souza"))
				.andExpect(jsonPath("$.conteudo[0].proprietarioId").isNumber());
	}

	private void criar(String proprietario, String municipio, String uf, double area) throws Exception {
		mockMvc.perform(post("/api/imoveis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CorposDeTeste.imovel(proprietario, municipio, uf, area)))
				.andExpect(status().isCreated());
	}
}
