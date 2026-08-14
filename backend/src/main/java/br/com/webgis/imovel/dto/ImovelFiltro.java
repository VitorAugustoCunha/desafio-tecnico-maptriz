package br.com.webgis.imovel.dto;

import jakarta.validation.constraints.Size;

/**
 * Filtros da listagem, aplicados <strong>no servidor</strong>.
 *
 * <p>Filtrar no cliente exigiria trazer a tabela inteira — que e exatamente o
 * problema do codigo original e o que a tarefa 6 pede para resolver.
 *
 * @param proprietarioId   filtro exato por titular, usado pela pagina de proprietarios
 * @param proprietarioNome busca parcial, sem diferenciar maiusculas
 * @param municipio        busca parcial, sem diferenciar maiusculas
 * @param ativo            {@code null} traz ativos e inativos
 */
public record ImovelFiltro(
		Long proprietarioId,

		@Size(max = 120, message = "no maximo 120 caracteres")
		String proprietarioNome,

		@Size(max = 120, message = "no maximo 120 caracteres")
		String municipio,

		Boolean ativo) {

	public ImovelFiltro {
		proprietarioNome = normalizar(proprietarioNome);
		municipio = normalizar(municipio);
	}

	/** Texto em branco e o mesmo que filtro ausente — evita um LIKE '%%' inutil. */
	private static String normalizar(String valor) {
		if (valor == null) {
			return null;
		}
		String limpo = valor.trim();
		return limpo.isEmpty() ? null : limpo;
	}

	public boolean vazio() {
		return proprietarioId == null && proprietarioNome == null && municipio == null && ativo == null;
	}
}
