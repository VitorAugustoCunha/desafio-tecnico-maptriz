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
}
