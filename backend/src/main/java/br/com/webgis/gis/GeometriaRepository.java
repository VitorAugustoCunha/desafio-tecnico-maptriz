package br.com.webgis.gis;

import java.math.BigDecimal;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Consultas espaciais que nao mapeiam para uma entidade.
 *
 * <p>Usa SQL nativa porque JPQL nao conhece funcoes do PostGIS — mas, ao
 * contrario do codigo original, <strong>todo</strong> valor entra por parametro
 * nomeado. Nao existe concatenacao de texto em nenhuma consulta deste projeto.
 */
@Repository
public class GeometriaRepository {

	/**
	 * Chave do advisory lock que serializa as escritas de geometria.
	 *
	 * <p>Constante unica para todo o cadastro: e o que garante que a checagem de
	 * sobreposicao e a gravacao aconteçam sem que outra transacao insira um lote
	 * conflitante no meio. Ver {@link GeometriaService} para o trade-off.
	 */
	static final long CHAVE_LOCK_GEOMETRIA = 8_314_2026L;

	/** Sentinela para "nenhum imovel a ignorar" — evita parametro nulo com tipo ambiguo na SQL nativa. */
	static final long SEM_IMOVEL_ATUAL = -1L;

	private final EntityManager em;

	public GeometriaRepository(EntityManager em) {
		this.em = em;
	}

	/**
	 * Bloqueia as escritas de geometria ate o fim da transacao corrente.
	 *
	 * <p>{@code pg_advisory_xact_lock} e liberado automaticamente no commit ou no
	 * rollback — nao ha risco de deixar o lock preso se a transacao falhar.
	 */
	public void bloquearEscritaGeometrica() {
		em.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:chave)) AS bloqueio")
				.setParameter("chave", CHAVE_LOCK_GEOMETRIA)
				.getSingleResult();
	}

	/**
	 * Monta o retangulo do lote e, na mesma ida ao banco, responde duas coisas:
	 * se a geometria gerada e valida ({@code ST_IsValid}) e qual imovel ja
	 * ocupa aquele espaco ({@code ST_Intersects}), se houver.
	 *
	 * <p>{@code ST_Intersects} — e nao {@code ST_Overlaps} — porque o enunciado diz
	 * "intersecta ou sobrepoe": encostar a borda ja conta como conflito.
	 * {@code ST_Overlaps} devolveria falso justamente para lotes que so se tocam,
	 * e para um dentro do outro.
	 *
	 * @param idImovelAtual id a ignorar na comparacao (o proprio imovel, ao editar),
	 *                      ou {@link #SEM_IMOVEL_ATUAL} na criacao
	 */
	public ResultadoVerificacao verificar(long idImovelAtual, BigDecimal longitude, BigDecimal latitude,
			BigDecimal larguraM, BigDecimal comprimentoM) {

		Query consulta = em.createNativeQuery("""
				WITH retangulo AS (
				    SELECT webgis_retangulo(
				               CAST(:longitude     AS double precision),
				               CAST(:latitude      AS double precision),
				               CAST(:larguraM      AS double precision),
				               CAST(:comprimentoM  AS double precision)) AS g
				)
				SELECT
				    (SELECT ST_IsValid(g) FROM retangulo)                       AS valida,
				    (SELECT i.id
				       FROM imovel i, retangulo r
				      WHERE i.geom IS NOT NULL
				        AND i.id <> :idImovelAtual
				        AND ST_Intersects(i.geom, r.g)
				      ORDER BY i.id
				      LIMIT 1)                                                  AS id_conflitante
				""");

		consulta.setParameter("longitude", longitude.doubleValue());
		consulta.setParameter("latitude", latitude.doubleValue());
		consulta.setParameter("larguraM", larguraM.doubleValue());
		consulta.setParameter("comprimentoM", comprimentoM.doubleValue());
		consulta.setParameter("idImovelAtual", idImovelAtual);

		Object[] linha = (Object[]) consulta.getSingleResult();

		boolean valida = Boolean.TRUE.equals(linha[0]);
		Long idConflitante = linha[1] == null ? null : ((Number) linha[1]).longValue();

		return new ResultadoVerificacao(valida, idConflitante);
	}

	/**
	 * Analisa um poligono desenhado, <b>sem</b> compara-lo com os demais.
	 *
	 * <p>Separado da checagem de conflito de proposito: {@code ST_Intersects}
	 * sobre geometria invalida pode lancar erro de topologia do GEOS, o que
	 * viraria {@code 500} e ainda envenenaria a transacao. Primeiro se confirma
	 * que o desenho e um poligono valido; so depois ele e comparado.
	 *
	 * <p>Devolve tambem a area e o centroide ja calculados, porque no modo
	 * desenho os dois passam a ser <b>derivados</b> da geometria — guardar uma
	 * area que contradiz o poligono seria manter duas versoes da mesma verdade.
	 */
	public AnaliseDoPoligono analisar(String geoJson) {
		Object[] linha = (Object[]) em.createNativeQuery("""
				WITH entrada AS (
				    SELECT ST_Transform(
				               ST_SetSRID(ST_GeomFromGeoJSON(CAST(:geoJson AS text)), 4326),
				               31982) AS g
				)
				SELECT ST_IsValid(e.g)                                          AS valida,
				       ST_GeometryType(e.g) = 'ST_Polygon'                      AS eh_poligono,
				       ST_Area(e.g)                                             AS area,
				       ST_Y(ST_Transform(ST_Centroid(e.g), 4326))               AS latitude,
				       ST_X(ST_Transform(ST_Centroid(e.g), 4326))               AS longitude
				  FROM entrada e
				""")
				.setParameter("geoJson", geoJson)
				.getSingleResult();

		return new AnaliseDoPoligono(
				Boolean.TRUE.equals(linha[0]),
				Boolean.TRUE.equals(linha[1]),
				((Number) linha[2]).doubleValue(),
				((Number) linha[3]).doubleValue(),
				((Number) linha[4]).doubleValue());
	}

	/** Primeiro imovel cujo poligono intersecta o desenho, se houver. */
	public Long conflitoComPoligono(long idImovelAtual, String geoJson) {
		@SuppressWarnings("unchecked")
		java.util.List<Object> resultado = em.createNativeQuery("""
				WITH entrada AS (
				    SELECT ST_Transform(
				               ST_SetSRID(ST_GeomFromGeoJSON(CAST(:geoJson AS text)), 4326),
				               31982) AS g
				)
				SELECT i.id
				  FROM imovel i, entrada e
				 WHERE i.geom IS NOT NULL
				   AND i.id <> :idImovelAtual
				   AND ST_Intersects(i.geom, e.g)
				 ORDER BY i.id
				 LIMIT 1
				""")
				.setParameter("geoJson", geoJson)
				.setParameter("idImovelAtual", idImovelAtual)
				.getResultList();

		return resultado.isEmpty() ? null : ((Number) resultado.get(0)).longValue();
	}

	/** Grava o poligono desenhado, reprojetado de 4326 para 31982. */
	public int gravarPoligono(Long idImovel, String geoJson) {
		return em.createNativeQuery("""
				UPDATE imovel
				   SET geom = ST_Transform(
				                  ST_SetSRID(ST_GeomFromGeoJSON(CAST(:geoJson AS text)), 4326),
				                  31982)
				 WHERE id = :id
				""")
				.setParameter("geoJson", geoJson)
				.setParameter("id", idImovel)
				.executeUpdate();
	}

	/** Geometria do imovel em GeoJSON (4326), para a tela de edicao redesenhar o lote. */
	public String geoJsonDoImovel(Long idImovel) {
		@SuppressWarnings("unchecked")
		java.util.List<Object> resultado = em.createNativeQuery("""
				SELECT ST_AsGeoJSON(ST_Transform(geom, 4326), 7)
				  FROM imovel
				 WHERE id = :id AND geom IS NOT NULL
				""")
				.setParameter("id", idImovel)
				.getResultList();

		return resultado.isEmpty() ? null : (String) resultado.get(0);
	}

	/** Area do retangulo em m², calculada pelo PostGIS no plano projetado. Usado nos testes de geometria. */
	public double areaDoRetangulo(BigDecimal longitude, BigDecimal latitude,
			BigDecimal larguraM, BigDecimal comprimentoM) {

		Object resultado = em.createNativeQuery("""
				SELECT ST_Area(webgis_retangulo(
				           CAST(:longitude    AS double precision),
				           CAST(:latitude     AS double precision),
				           CAST(:larguraM     AS double precision),
				           CAST(:comprimentoM AS double precision)))
				""")
				.setParameter("longitude", longitude.doubleValue())
				.setParameter("latitude", latitude.doubleValue())
				.setParameter("larguraM", larguraM.doubleValue())
				.setParameter("comprimentoM", comprimentoM.doubleValue())
				.getSingleResult();

		return ((Number) resultado).doubleValue();
	}

	public record ResultadoVerificacao(boolean geometriaValida, Long idImovelConflitante) {

		public boolean temConflito() {
			return idImovelConflitante != null;
		}
	}

	/**
	 * Resultado da analise de um poligono desenhado.
	 *
	 * @param areaM2    area no plano projetado (31982), em metros quadrados
	 * @param latitude  centroide reprojetado para 4326 — vira o ponto do imovel
	 * @param longitude centroide reprojetado para 4326
	 */
	public record AnaliseDoPoligono(
			boolean valida,
			boolean ehPoligono,
			double areaM2,
			double latitude,
			double longitude) {
	}
}
