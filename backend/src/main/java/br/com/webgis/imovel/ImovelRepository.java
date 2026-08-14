package br.com.webgis.imovel;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

/**
 * Acesso a imoveis.
 *
 * <p>Substitui o {@code EntityManager} com SQL concatenada do codigo original.
 * Toda consulta daqui e parametrizada — nao existe caminho em que valor vindo do
 * usuario vire texto de SQL.
 */
public interface ImovelRepository extends JpaRepository<Imovel, Long>, JpaSpecificationExecutor<Imovel> {

	/**
	 * Pagina da listagem. O {@code @EntityGraph} carrega o proprietario na mesma
	 * consulta: sem ele, exibir o nome do titular de cada linha dispararia um
	 * SELECT por linha (N+1).
	 */
	@Override
	@NonNull
	@EntityGraph(attributePaths = "proprietario")
	Page<Imovel> findAll(Specification<Imovel> especificacao, @NonNull Pageable paginacao);

	@EntityGraph(attributePaths = "proprietario")
	Optional<Imovel> findWithProprietarioById(Long id);

	long countByProprietarioId(Long proprietarioId);

	/**
	 * Gera o poligono do lote em EPSG:31982 e o grava.
	 *
	 * <p>A construcao fica na funcao SQL {@code webgis_retangulo}: a projecao
	 * 4326 -> 31982 e feita pelo PROJ, a mesma engine que depois compara as
	 * geometrias, entao nao ha divergencia entre o que a aplicacao calcula e o
	 * que o banco valida (ver docs/DECISIONS.md, ADR-004).
	 */
	@Modifying
	@Query(value = """
			UPDATE imovel
			   SET geom = webgis_retangulo(
			           CAST(:longitude AS double precision),
			           CAST(:latitude AS double precision),
			           CAST(:larguraM AS double precision),
			           CAST(:comprimentoM AS double precision))
			 WHERE id = :id
			""", nativeQuery = true)
	int gravarGeometria(@Param("id") Long id,
			@Param("longitude") double longitude,
			@Param("latitude") double latitude,
			@Param("larguraM") double larguraM,
			@Param("comprimentoM") double comprimentoM);

	/** Remove a geometria quando o imovel deixa de ter dimensoes. */
	@Modifying
	@Query(value = "UPDATE imovel SET geom = NULL WHERE id = :id", nativeQuery = true)
	int limparGeometria(@Param("id") Long id);

	@Query(value = "SELECT geom IS NOT NULL FROM imovel WHERE id = :id", nativeQuery = true)
	Optional<Boolean> possuiGeometria(@Param("id") Long id);
}
