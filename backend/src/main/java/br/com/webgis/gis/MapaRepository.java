package br.com.webgis.gis;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Repository;

import br.com.webgis.gis.dto.Feicao;
import br.com.webgis.gis.dto.PropriedadesCluster;
import br.com.webgis.gis.dto.PropriedadesImovel;
import jakarta.persistence.EntityManager;

/**
 * Consultas do mapa, sempre recortadas pelo viewport.
 *
 * <p>Nao existe consulta que traga o cadastro inteiro para o mapa. O recorte usa
 * o operador {@code &&} (interseccao de bounding box), que e o que aciona o
 * indice GiST {@code idx_imovel_ponto_gist}; {@code ST_Intersects} sozinho
 * tambem usaria o indice, mas {@code &&} deixa explicito que a intencao e o
 * filtro barato por envelope.
 */
@Repository
public class MapaRepository {

	private final EntityManager em;

	public MapaRepository(EntityManager em) {
		this.em = em;
	}

	/**
	 * Feicoes individuais do viewport.
	 *
	 * <p>Quando o imovel tem poligono, devolve o poligono reprojetado para 4326
	 * (o mapa trabalha em graus); quando nao tem — imovel legado —, devolve o
	 * ponto. Assim a tela funciona com a base atual e com a nova ao mesmo tempo.
	 *
	 * <p>{@code ST_AsGeoJSON(..., 6)} corta em 6 casas decimais: ~10 cm de
	 * precisao, mais do que suficiente para exibicao, e bem menos bytes que o
	 * padrao de 15 casas.
	 */
	@SuppressWarnings("unchecked")
	public List<Feicao> feicoesNoViewport(double minLon, double minLat, double maxLon, double maxLat,
			boolean apenasAtivos, int limite) {

		List<Object[]> linhas = em.createNativeQuery("""
				SELECT i.id,
				       p.id   AS proprietario_id,
				       p.nome AS proprietario_nome,
				       i.municipio,
				       i.uf,
				       i.area_m2,
				       i.geom IS NOT NULL AS tem_poligono,
				       COALESCE(ST_AsGeoJSON(ST_Transform(i.geom, 4326), 6),
				                ST_AsGeoJSON(i.ponto, 6)) AS geometria
				  FROM imovel i
				  JOIN proprietario p ON p.id = i.proprietario_id
				 WHERE i.ponto && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
				   AND (:apenasAtivos = FALSE OR i.ativo = TRUE)
				 ORDER BY i.id
				 LIMIT :limite
				""")
				.setParameter("minLon", minLon)
				.setParameter("minLat", minLat)
				.setParameter("maxLon", maxLon)
				.setParameter("maxLat", maxLat)
				.setParameter("apenasAtivos", apenasAtivos)
				.setParameter("limite", limite)
				.getResultList();

		return linhas.stream().map(MapaRepository::paraFeicaoDeImovel).toList();
	}

	/**
	 * Agregacao por celula de grade, para os zooms afastados.
	 *
	 * <p>O agrupamento e por {@code ST_SnapToGrid}, e a posicao devolvida e o
	 * centroide real dos pontos da celula — nao o centro da celula —, para o
	 * marcador cair sobre a mancha urbana e nao num ponto arbitrario da grade.
	 *
	 * <p>Fazer isso no banco, e nao no navegador, e o ponto: em vez de baixar
	 * 500 mil pontos para agrupar no cliente, a rede carrega algumas dezenas de
	 * agregados.
	 */
	@SuppressWarnings("unchecked")
	public List<Feicao> clustersNoViewport(double minLon, double minLat, double maxLon, double maxLat,
			boolean apenasAtivos, double tamanhoCelula, int limite) {

		List<Object[]> linhas = em.createNativeQuery("""
				SELECT ST_AsGeoJSON(ST_Centroid(ST_Collect(i.ponto)), 6) AS geometria,
				       COUNT(*)                                          AS quantidade
				  FROM imovel i
				 WHERE i.ponto && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
				   AND (:apenasAtivos = FALSE OR i.ativo = TRUE)
				 GROUP BY ST_SnapToGrid(i.ponto, :tamanhoCelula, :tamanhoCelula)
				 ORDER BY COUNT(*) DESC
				 LIMIT :limite
				""")
				.setParameter("minLon", minLon)
				.setParameter("minLat", minLat)
				.setParameter("maxLon", maxLon)
				.setParameter("maxLat", maxLat)
				.setParameter("apenasAtivos", apenasAtivos)
				.setParameter("tamanhoCelula", tamanhoCelula)
				.setParameter("limite", limite)
				.getResultList();

		return linhas.stream()
				.map(linha -> Feicao.de((String) linha[0], new PropriedadesCluster(((Number) linha[1]).longValue())))
				.toList();
	}

	/**
	 * Lote de feicoes para exportacao, paginado por id (keyset).
	 *
	 * <p>Keyset e nao {@code OFFSET}: em exportacao completa, o offset fica cada
	 * vez mais caro conforme avanca, porque o banco precisa descartar todas as
	 * linhas anteriores a cada lote.
	 */
	@SuppressWarnings("unchecked")
	public List<Feicao> loteParaExportacao(long idMinimoExclusivo, Long proprietarioId, String municipio, int tamanho) {

		List<Object[]> linhas = em.createNativeQuery("""
				SELECT i.id,
				       p.id   AS proprietario_id,
				       p.nome AS proprietario_nome,
				       i.municipio,
				       i.uf,
				       i.area_m2,
				       i.geom IS NOT NULL AS tem_poligono,
				       COALESCE(ST_AsGeoJSON(ST_Transform(i.geom, 4326), 6),
				                ST_AsGeoJSON(i.ponto, 6)) AS geometria
				  FROM imovel i
				  JOIN proprietario p ON p.id = i.proprietario_id
				 WHERE i.id > :idMinimo
				   AND (CAST(:proprietarioId AS bigint) IS NULL
				        OR i.proprietario_id = CAST(:proprietarioId AS bigint))
				   AND (CAST(:municipio AS text) IS NULL
				        OR LOWER(i.municipio) LIKE LOWER(CAST(:municipio AS text)))
				 ORDER BY i.id
				 LIMIT :tamanho
				""")
				.setParameter("idMinimo", idMinimoExclusivo)
				.setParameter("proprietarioId", proprietarioId)
				.setParameter("municipio", municipio)
				.setParameter("tamanho", tamanho)
				.getResultList();

		return linhas.stream().map(MapaRepository::paraFeicaoDeImovel).toList();
	}

	private static Feicao paraFeicaoDeImovel(Object[] linha) {
		PropriedadesImovel propriedades = new PropriedadesImovel(
				((Number) linha[0]).longValue(),
				((Number) linha[1]).longValue(),
				(String) linha[2],
				(String) linha[3],
				(String) linha[4],
				(BigDecimal) linha[5],
				Boolean.TRUE.equals(linha[6]));

		return Feicao.de((String) linha[7], propriedades);
	}

	/** Id do ultimo imovel de um lote, para o keyset do proximo. */
	public static long ultimoId(List<Feicao> lote) {
		Feicao ultima = lote.get(lote.size() - 1);
		return ((PropriedadesImovel) ultima.properties()).id();
	}
}
