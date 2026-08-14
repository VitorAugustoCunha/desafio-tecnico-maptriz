package br.com.webgis.imovel;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.data.domain.Sort;

/**
 * Whitelist de ordenacao da listagem.
 *
 * <p>Aceitar o nome do campo direto da query string deixaria o cliente ordenar
 * por qualquer atributo mapeado — inclusive por caminhos que forcam join e
 * varredura completa — e vazaria o modelo interno no contrato da API. Aqui o
 * cliente escolhe de um conjunto fechado, e cada opcao tem indice em
 * {@code V4__indices_listagem.sql}.
 *
 * <p>Todas as ordenacoes desempatam por {@code id}: sem ordem total, a paginacao
 * por offset pode repetir ou pular linhas entre paginas quando ha valores iguais
 * na coluna ordenada.
 */
public enum OrdenacaoImovel {

	ID("id"),
	MUNICIPIO("municipio"),
	AREA("areaM2"),
	CRIADO_EM("criadoEm"),
	PROPRIETARIO("proprietario.nome");

	private final String propriedade;

	OrdenacaoImovel(String propriedade) {
		this.propriedade = propriedade;
	}

	public Sort paraSort(Sort.Direction direcao) {
		Sort ordem = Sort.by(direcao, propriedade);
		return this == ID ? ordem : ordem.and(Sort.by(direcao, "id"));
	}

	public static OrdenacaoImovel de(String valor) {
		if (valor == null || valor.isBlank()) {
			return ID;
		}
		return Arrays.stream(values())
				.filter(o -> o.name().equalsIgnoreCase(valor.trim()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"ordenacao invalida: '%s'. Valores aceitos: %s"
								.formatted(valor, Arrays.stream(values())
										.map(o -> o.name().toLowerCase(Locale.ROOT))
										.toList())));
	}
}
