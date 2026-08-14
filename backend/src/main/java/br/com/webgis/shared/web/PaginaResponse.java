package br.com.webgis.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Envelope de paginacao proprio da API.
 *
 * <p>Existe para nao serializar o {@code Page} do Spring Data direto: o formato
 * dele nao e um contrato estavel (o proprio Spring Boot 3.3 passou a avisar
 * sobre isso) e expoe detalhes internos como {@code pageable} e {@code sort}
 * que o cliente nao precisa conhecer.
 */
public record PaginaResponse<T>(
		List<T> conteudo,
		int pagina,
		int tamanho,
		long totalDeElementos,
		int totalDePaginas,
		boolean primeira,
		boolean ultima) {

	public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> mapeador) {
		return new PaginaResponse<>(
				pagina.getContent().stream().map(mapeador).toList(),
				pagina.getNumber(),
				pagina.getSize(),
				pagina.getTotalElements(),
				pagina.getTotalPages(),
				pagina.isFirst(),
				pagina.isLast());
	}

	public static <T> PaginaResponse<T> de(Page<T> pagina) {
		return de(pagina, Function.identity());
	}
}
