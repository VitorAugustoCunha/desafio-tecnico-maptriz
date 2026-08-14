package br.com.webgis.proprietario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Normalizacao de nome de proprietario")
class NomeNormalizadorTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Maria Aparecida Souza        | maria aparecida souza",
			"  Maria Aparecida Souza      | maria aparecida souza",
			"Maria   Aparecida   Souza    | maria aparecida souza",
			"MARIA APARECIDA SOUZA        | maria aparecida souza",
			"'  MARIA   Aparecida SOUZA ' | maria aparecida souza"
	})
	@DisplayName("deduplica variacoes de espacamento e caixa")
	void deduplicaEspacamentoECaixa(String entrada, String esperado) {
		assertThat(NomeNormalizador.normalizar(entrada)).isEqualTo(esperado);
	}

	@Test
	@DisplayName("NAO remove acentos: unir titulares distintos e irreversivel")
	void preservaAcentos() {
		assertThat(NomeNormalizador.normalizar("José da Silva"))
				.isNotEqualTo(NomeNormalizador.normalizar("Jose da Silva"));
	}

	@Test
	@DisplayName("a forma de exibicao arruma espacos sem mexer na caixa")
	void formaDeExibicao() {
		assertThat(NomeNormalizador.exibicao("  João   Carlos  Ferreira ")).isEqualTo("João Carlos Ferreira");
	}

	@Test
	@DisplayName("recusa nome nulo em vez de propagar NullPointerException")
	void recusaNulo() {
		assertThatThrownBy(() -> NomeNormalizador.normalizar(null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("trata tabulacao e quebra de linha como espaco")
	void tratraOutrosEspacos() {
		assertThat(NomeNormalizador.normalizar("Ana\tBeatriz\nLima")).isEqualTo("ana beatriz lima");
	}
}
