package br.com.webgis.imovel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.webgis.imovel.dto.ImovelRequest;

@DisplayName("Mapeamento e normalizacao do imovel")
class ImovelMapperTest {

	@Test
	@DisplayName("normaliza UF para maiusculas")
	void normalizaUf() {
		DadosDoImovel dados = ImovelMapper.paraDados(requisicao("sp", null, null, new BigDecimal("100")));

		assertThat(dados.uf()).isEqualTo("SP");
	}

	@Test
	@DisplayName("recorta espacos e colapsa espacos internos dos textos")
	void normalizaTextos() {
		ImovelRequest request = new ImovelRequest(
				"Maria Souza", "  Sao   Paulo  ", "SP", " Pinheiros ", "Rua   dos  Pinheiros ", " 1245 ",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), new BigDecimal("100"), null, null, null, true);

		DadosDoImovel dados = ImovelMapper.paraDados(request);

		assertThat(dados.municipio()).isEqualTo("Sao Paulo");
		assertThat(dados.bairro()).isEqualTo("Pinheiros");
		assertThat(dados.rua()).isEqualTo("Rua dos Pinheiros");
		assertThat(dados.numero()).isEqualTo("1245");
	}

	@Test
	@DisplayName("com dimensoes, a area e derivada de largura x comprimento")
	void areaDerivadaDasDimensoes() {
		DadosDoImovel dados = ImovelMapper.paraDados(
				requisicao("SP", new BigDecimal("20"), new BigDecimal("50"), new BigDecimal("999999")));

		assertThat(dados.areaM2())
				.as("o valor enviado em areaM2 e ignorado para nao contradizer o poligono gravado")
				.isEqualByComparingTo("1000.00");
	}

	@Test
	@DisplayName("sem dimensoes, a area informada e preservada")
	void areaInformadaPreservada() {
		DadosDoImovel dados = ImovelMapper.paraDados(requisicao("SP", null, null, new BigDecimal("320.50")));

		assertThat(dados.areaM2()).isEqualByComparingTo("320.50");
	}

	@Test
	@DisplayName("area derivada arredonda para 2 casas")
	void areaDerivadaArredonda() {
		DadosDoImovel dados = ImovelMapper.paraDados(
				requisicao("SP", new BigDecimal("10.55"), new BigDecimal("10.55"), null));

		// 10.55 * 10.55 = 111.3025 -> 111.30
		assertThat(dados.areaM2()).isEqualByComparingTo("111.30");
	}

	private static ImovelRequest requisicao(String uf, BigDecimal largura, BigDecimal comprimento, BigDecimal area) {
		return new ImovelRequest("Maria Souza", "Sao Paulo", uf, "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), area, largura, comprimento, null, true);
	}
}
