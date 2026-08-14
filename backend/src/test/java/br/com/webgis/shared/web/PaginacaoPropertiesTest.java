package br.com.webgis.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Limites de paginacao")
class PaginacaoPropertiesTest {

	private final PaginacaoProperties propriedades = new PaginacaoProperties(20, 100);

	@Test
	@DisplayName("usa o padrao quando o tamanho nao vem")
	void usaPadrao() {
		assertThat(propriedades.ajustar(null)).isEqualTo(20);
	}

	@Test
	@DisplayName("usa o padrao para tamanho invalido")
	void usaPadraoParaInvalido() {
		assertThat(propriedades.ajustar(0)).isEqualTo(20);
		assertThat(propriedades.ajustar(-5)).isEqualTo(20);
	}

	@Test
	@DisplayName("respeita o tamanho pedido quando esta dentro do limite")
	void respeitaPedido() {
		assertThat(propriedades.ajustar(50)).isEqualTo(50);
	}

	@Test
	@DisplayName("corta no teto: nao existe caminho para pedir a tabela inteira")
	void cortaNoTeto() {
		assertThat(propriedades.ajustar(1_000_000)).isEqualTo(100);
	}

	@Test
	@DisplayName("aplica valores padrao quando a configuracao vem vazia")
	void valoresPadrao() {
		PaginacaoProperties vazia = new PaginacaoProperties(null, null);

		assertThat(vazia.tamanhoPadrao()).isEqualTo(20);
		assertThat(vazia.tamanhoMaximo()).isEqualTo(100);
	}
}
