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
	 * <p>A contagem vem por <b>subconsulta correlacionada</b>, e nao por
	 * {@code LEFT JOIN ... GROUP BY}. A diferenca e grande em volume: com o
	 * {@code GROUP BY} sobre a juncao, o banco precisa varrer e agrupar a tabela
	 * inteira de imoveis para depois devolver as 20 linhas da pagina. Medido com
	 * 500 mil imoveis, esse formato levava ~573 ms (p50); com a subconsulta,
	 * apenas os 20 titulares da pagina sao contados, cada um por
	 * {@code idx_imovel_proprietario_id}. Os numeros estao em docs/PERFORMANCE.md.
	 *
	 * <p>Continua sendo uma unica ida ao banco — nao e N+1: o N+1 seria a
	 * aplicacao emitir uma consulta por linha, em ida e volta pela rede.
	 *
	 * <p>O filtro chega sempre como padrao LIKE ({@code '%%'} quando nao ha busca),
	 * o que evita parametro nulo dentro da consulta e mantem um unico plano.
	 */
	@Query(value = """
			SELECT new br.com.webgis.proprietario.dto.ProprietarioListItem(
			           p.id,
			           p.nome,
			           (SELECT COUNT(i.id) FROM Imovel i WHERE i.proprietario = p))
			  FROM Proprietario p
			 WHERE LOWER(p.nome) LIKE :padraoNome
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
