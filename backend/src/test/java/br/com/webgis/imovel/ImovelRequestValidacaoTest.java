package br.com.webgis.imovel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.webgis.imovel.dto.ImovelRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Validacao do corpo de imovel.
 *
 * <p>Cada caso aqui reproduz um payload que o codigo original aceitava e que
 * terminava em dado corrompido ou HTTP 500.
 */
@DisplayName("Validacao do ImovelRequest")
class ImovelRequestValidacaoTest {

	private static ValidatorFactory fabrica;
	private static Validator validador;

	@BeforeAll
	static void iniciar() {
		fabrica = Validation.buildDefaultValidatorFactory();
		validador = fabrica.getValidator();
	}

	@AfterAll
	static void encerrar() {
		fabrica.close();
	}

	@Test
	@DisplayName("aceita um imovel completo e valido")
	void aceitaValido() {
		assertThat(validador.validate(valido())).isEmpty();
	}

	@Test
	@DisplayName("recusa latitude fora da faixa (o baseline aceitava 999)")
	void recusaLatitudeForaDaFaixa() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "SP", "Centro", "Rua A", "10",
				new BigDecimal("999"), new BigDecimal("-46.6"), new BigDecimal("100"), null, null, null, true);

		assertThat(campos(request)).contains("latitude");
	}

	@Test
	@DisplayName("recusa longitude fora da faixa")
	void recusaLongitudeForaDaFaixa() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "SP", "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-999"), new BigDecimal("100"), null, null, null, true);

		assertThat(campos(request)).contains("longitude");
	}

	@Test
	@DisplayName("recusa UF que nao tem 2 letras (o baseline devolvia 500)")
	void recusaUfInvalida() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "XXXXX", "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), new BigDecimal("100"), null, null, null, true);

		assertThat(campos(request)).contains("uf");
	}

	@Test
	@DisplayName("recusa textos obrigatorios em branco")
	void recusaTextosEmBranco() {
		ImovelRequest request = new ImovelRequest("  ", "", "SP", "  ", "", " ",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), new BigDecimal("100"), null, null, null, true);

		assertThat(campos(request))
				.contains("proprietarioNome", "municipio", "bairro", "rua", "numero");
	}

	@Test
	@DisplayName("recusa area negativa ou zero")
	void recusaAreaNaoPositiva() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "SP", "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), new BigDecimal("-50"), null, null, null, true);

		assertThat(campos(request)).contains("areaM2");
	}

	@Test
	@DisplayName("recusa 'ativo' ausente — no baseline isso mudava o estado do imovel sozinho")
	void recusaAtivoAusente() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "SP", "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), new BigDecimal("100"), null, null, null, null);

		assertThat(campos(request)).contains("ativo");
	}

	@Test
	@DisplayName("recusa coordenada nula — no baseline isso apagava a coordenada gravada")
	void recusaCoordenadaNula() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "SP", "Centro", "Rua A", "10",
				null, null, new BigDecimal("100"), null, null, null, true);

		assertThat(campos(request)).contains("latitude", "longitude");
	}

	@Test
	@DisplayName("recusa apenas uma das dimensoes: meia dimensao nao monta retangulo")
	void recusaDimensaoSolta() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "SP", "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), new BigDecimal("100"),
				new BigDecimal("20"), null, null, true);

		assertThat(campos(request)).contains("dimensoesEmPar");
	}

	@Test
	@DisplayName("recusa imovel sem area e sem dimensoes")
	void recusaSemTamanho() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "SP", "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), null, null, null, null, true);

		assertThat(campos(request)).contains("areaInformavel");
	}

	@Test
	@DisplayName("aceita dimensoes sem area: a area passa a ser derivada")
	void aceitaDimensoesSemArea() {
		ImovelRequest request = new ImovelRequest("Maria", "Sao Paulo", "SP", "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), null,
				new BigDecimal("20"), new BigDecimal("50"), null, true);

		assertThat(validador.validate(request)).isEmpty();
	}

	@Test
	@DisplayName("recusa texto acima do tamanho da coluna, em vez de estourar no banco")
	void recusaTextoLongo() {
		ImovelRequest request = new ImovelRequest("Maria", "M".repeat(121), "SP", "Centro", "Rua A", "10",
				new BigDecimal("-23.5"), new BigDecimal("-46.6"), new BigDecimal("100"), null, null, null, true);

		assertThat(campos(request)).contains("municipio");
	}

	private static Set<String> campos(ImovelRequest request) {
		return validador.validate(request).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(java.util.stream.Collectors.toSet());
	}

	private static ImovelRequest valido() {
		return new ImovelRequest("Maria Souza", "Sao Paulo", "SP", "Pinheiros", "Rua dos Pinheiros", "1245",
				new BigDecimal("-23.5629"), new BigDecimal("-46.6944"), new BigDecimal("320.50"), null, null, null, true);
	}
}
