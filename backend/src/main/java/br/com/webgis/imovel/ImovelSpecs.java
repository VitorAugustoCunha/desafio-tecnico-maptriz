package br.com.webgis.imovel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import br.com.webgis.imovel.dto.ImovelFiltro;
import br.com.webgis.proprietario.Proprietario;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Predicados da listagem.
 *
 * <p>Os filtros sao adicionados <strong>somente quando preenchidos</strong>, em
 * vez do padrao {@code (:param is null or coluna = :param)}. A diferenca importa
 * para o planejador: com o predicado ausente, o PostgreSQL escolhe o plano do
 * caso real; com o {@code OR :param IS NULL}, ele precisa produzir um plano que
 * sirva para os dois casos e costuma abrir mao do indice.
 *
 * <p>As comparacoes textuais usam {@code lower(coluna) LIKE lower(?)} — a mesma
 * expressao indexada por {@code idx_imovel_municipio_trgm} e
 * {@code idx_proprietario_nome_trgm} (GIN + pg_trgm).
 *
 * <p>O carregamento do proprietario junto com a pagina fica a cargo do
 * {@code @EntityGraph} em {@link ImovelRepository}, nao daqui: assim a consulta
 * de contagem nao arrasta join de leitura.
 */
final class ImovelSpecs {

	private ImovelSpecs() {
	}

	static Specification<Imovel> de(ImovelFiltro filtro) {
		return (raiz, consulta, cb) -> {
			List<Predicate> predicados = new ArrayList<>();

			if (filtro.proprietarioId() != null) {
				predicados.add(cb.equal(raiz.get("proprietario").get("id"), filtro.proprietarioId()));
			}

			if (filtro.proprietarioNome() != null) {
				Join<Imovel, Proprietario> proprietario = juntarProprietario(raiz);
				predicados.add(cb.like(cb.lower(proprietario.get("nome")), contem(filtro.proprietarioNome())));
			}

			if (filtro.municipio() != null) {
				predicados.add(cb.like(cb.lower(raiz.get("municipio")), contem(filtro.municipio())));
			}

			if (filtro.ativo() != null) {
				predicados.add(cb.equal(raiz.get("ativo"), filtro.ativo()));
			}

			return predicados.isEmpty() ? cb.conjunction() : cb.and(predicados.toArray(Predicate[]::new));
		};
	}

	/** Reaproveita o join do proprietario se ele ja existir na consulta. */
	private static Join<Imovel, Proprietario> juntarProprietario(Root<Imovel> raiz) {
		return raiz.getJoins().stream()
				.filter(join -> "proprietario".equals(join.getAttribute().getName()))
				.map(join -> {
					@SuppressWarnings("unchecked")
					Join<Imovel, Proprietario> existente = (Join<Imovel, Proprietario>) join;
					return existente;
				})
				.findFirst()
				.orElseGet(() -> raiz.join("proprietario", JoinType.INNER));
	}

	/** Escapa os curingas do LIKE para que o texto do usuario seja tratado como literal. */
	private static String contem(String texto) {
		String escapado = texto.toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + escapado + "%";
	}
}
