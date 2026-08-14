package br.com.webgis.imovel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

@DisplayName("Whitelist de ordenacao")
class OrdenacaoImovelTest {

	@Test
	@DisplayName("sem ordenacao informada, ordena por id")
	void padraoPorId() {
		assertThat(OrdenacaoImovel.de(null)).isEqualTo(OrdenacaoImovel.ID);
		assertThat(OrdenacaoImovel.de("  ")).isEqualTo(OrdenacaoImovel.ID);
	}

	@Test
	@DisplayName("aceita os valores da whitelist sem diferenciar caixa")
	void aceitaWhitelist() {
		assertThat(OrdenacaoImovel.de("municipio")).isEqualTo(OrdenacaoImovel.MUNICIPIO);
		assertThat(OrdenacaoImovel.de("MUNICIPIO")).isEqualTo(OrdenacaoImovel.MUNICIPIO);
		assertThat(OrdenacaoImovel.de(" criado_em ")).isEqualTo(OrdenacaoImovel.CRIADO_EM);
	}

	@Test
	@DisplayName("recusa campo fora da whitelist")
	void recusaCampoDesconhecido() {
		assertThatThrownBy(() -> OrdenacaoImovel.de("senha"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ordenacao invalida");
	}

	@Test
	@DisplayName("toda ordenacao desempata por id para a paginacao ser estavel")
	void desempataPorId() {
		Sort ordem = OrdenacaoImovel.MUNICIPIO.paraSort(Sort.Direction.ASC);

		assertThat(ordem.stream().map(Sort.Order::getProperty))
				.containsExactly("municipio", "id");
	}

	@Test
	@DisplayName("ordenar por id nao duplica o desempate")
	void idNaoDuplica() {
		assertThat(OrdenacaoImovel.ID.paraSort(Sort.Direction.DESC).stream().map(Sort.Order::getProperty))
				.containsExactly("id");
	}

	@Test
	@DisplayName("ordena por nome do proprietario atravessando o relacionamento")
	void ordenaPorProprietario() {
		assertThat(OrdenacaoImovel.PROPRIETARIO.paraSort(Sort.Direction.ASC).stream()
				.map(Sort.Order::getProperty))
				.containsExactly("proprietario.nome", "id");
	}
}
