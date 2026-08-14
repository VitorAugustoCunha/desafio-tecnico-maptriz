package br.com.webgis.proprietario;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.webgis.proprietario.dto.ProprietarioListItem;

public interface ProprietarioRepository extends JpaRepository<Proprietario, Long> {

	Optional<Proprietario> findByNomeNormalizado(String nomeNormalizado);

	boolean existsByNomeNormalizadoAndIdNot(String nomeNormalizado, Long id);

	/**
	 * Listagem com a contagem de imoveis de cada titular.
	 *
	 * <p>A contagem sai agregada na mesma consulta. Buscar os proprietarios e
	 * depois contar os imoveis de cada um seria o classico N+1 — 1 consulta para a
	 * pagina, mais uma por linha.
	 *
	 * <p>O filtro chega sempre como padrao LIKE ({@code '%%'} quando nao ha busca),
	 * o que evita parametro nulo dentro da consulta e mantem um unico plano.
	 */
	@Query(value = """
			SELECT new br.com.webgis.proprietario.dto.ProprietarioListItem(
			           p.id, p.nome, COUNT(i.id))
			  FROM Proprietario p
			  LEFT JOIN Imovel i ON i.proprietario = p
			 WHERE LOWER(p.nome) LIKE :padraoNome
			 GROUP BY p.id, p.nome
			""",
			countQuery = """
			SELECT COUNT(p)
			  FROM Proprietario p
			 WHERE LOWER(p.nome) LIKE :padraoNome
			""")
	Page<ProprietarioListItem> listar(@Param("padraoNome") String padraoNome, Pageable paginacao);

	@Query("""
			SELECT COUNT(i)
			  FROM Imovel i
			 WHERE i.proprietario.id = :proprietarioId
			""")
	long contarImoveis(@Param("proprietarioId") Long proprietarioId);
}
