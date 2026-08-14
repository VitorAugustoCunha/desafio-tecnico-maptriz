package br.com.webgis.imovel.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corpo de criacao e de atualizacao de imovel.
 *
 * <p>Cada limite aqui existe por um motivo concreto observado no baseline:
 *
 * <ul>
 *   <li>obrigatoriedade: no codigo original, um campo ausente virava a string
 *       {@code "null"} no banco, e um numerico ausente apagava o valor gravado —
 *       ambos com resposta {@code 200};</li>
 *   <li>faixas geograficas: latitude 999 era aceita pela API;</li>
 *   <li>tamanhos: espelham as colunas, para o erro sair como {@code 400} com a
 *       mensagem do campo em vez de {@code 500} por estouro de {@code varchar}.</li>
 * </ul>
 *
 * <p>{@code ativo} e obrigatorio de proposito: e o campo cuja ausencia,
 * no codigo original, mudava o estado do imovel sem o usuario pedir.
 */
public record ImovelRequest(

		@NotBlank(message = "informe o proprietario")
		@Size(max = 120, message = "no maximo 120 caracteres")
		String proprietarioNome,

		@NotBlank(message = "informe o municipio")
		@Size(max = 120, message = "no maximo 120 caracteres")
		String municipio,

		@NotBlank(message = "informe a UF")
		@Pattern(regexp = "^[A-Za-z]{2}$", message = "use a sigla de 2 letras, por exemplo SP")
		String uf,

		@NotBlank(message = "informe o bairro")
		@Size(max = 100, message = "no maximo 100 caracteres")
		String bairro,

		@NotBlank(message = "informe a rua")
		@Size(max = 150, message = "no maximo 150 caracteres")
		String rua,

		@NotBlank(message = "informe o numero")
		@Size(max = 10, message = "no maximo 10 caracteres")
		String numero,

		@NotNull(message = "informe a latitude")
		@DecimalMin(value = "-90", message = "a latitude deve estar entre -90 e 90")
		@DecimalMax(value = "90", message = "a latitude deve estar entre -90 e 90")
		@Digits(integer = 3, fraction = 7, message = "no maximo 7 casas decimais")
		BigDecimal latitude,

		@NotNull(message = "informe a longitude")
		@DecimalMin(value = "-180", message = "a longitude deve estar entre -180 e 180")
		@DecimalMax(value = "180", message = "a longitude deve estar entre -180 e 180")
		@Digits(integer = 3, fraction = 7, message = "no maximo 7 casas decimais")
		BigDecimal longitude,

		/** Ignorada quando largura e comprimento vem preenchidos: nesse caso a area e calculada. */
		@Positive(message = "a area deve ser maior que zero")
		@Digits(integer = 10, fraction = 2, message = "no maximo 2 casas decimais")
		BigDecimal areaM2,

		@Positive(message = "a largura deve ser maior que zero")
		@Digits(integer = 8, fraction = 2, message = "no maximo 2 casas decimais")
		BigDecimal larguraM,

		@Positive(message = "o comprimento deve ser maior que zero")
		@Digits(integer = 8, fraction = 2, message = "no maximo 2 casas decimais")
		BigDecimal comprimentoM,

		@NotNull(message = "informe se o imovel esta ativo")
		Boolean ativo) {

	/**
	 * Ou o imovel tem largura E comprimento (e ganha poligono), ou nao tem
	 * nenhuma das duas (imovel so com ponto). Meia dimensao nao monta retangulo.
	 */
	@AssertTrue(message = "informe largura e comprimento juntos, ou nenhum dos dois")
	public boolean isDimensoesEmPar() {
		return (larguraM == null) == (comprimentoM == null);
	}

	/** Sem dimensoes, a area precisa vir explicita — senao nao ha como saber o tamanho do lote. */
	@AssertTrue(message = "informe a area, ou largura e comprimento")
	public boolean isAreaInformavel() {
		return areaM2 != null || (larguraM != null && comprimentoM != null);
	}

	/** {@code true} quando o request pede geometria real (tarefa 8). */
	public boolean possuiDimensoes() {
		return larguraM != null && comprimentoM != null;
	}
}
