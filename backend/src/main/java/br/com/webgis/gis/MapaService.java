package br.com.webgis.gis;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.webgis.gis.dto.ColecaoDeFeicoes;
import br.com.webgis.gis.dto.Feicao;

/**
 * Monta a resposta do mapa a partir do viewport.
 *
 * <p><b>Onde o clustering acontece e por que.</b> O agrupamento e feito pelo
 * PostGIS, no {@code GROUP BY ST_SnapToGrid} da {@link MapaRepository}, e nao no
 * navegador. Agrupar no cliente exigiria baixar todos os pontos primeiro — que e
 * exatamente o custo que o cluster deveria evitar. Com a agregacao no banco, um
 * viewport com 500 mil imoveis devolve algumas dezenas de feicoes.
 *
 * <p>O Web Worker do frontend <b>nao</b> refaz esse clustering. Ele cuida do que
 * o banco nao tem como fazer: preparar os dados ja recebidos para desenho
 * (normalizacao, estatisticas, buffers de coordenadas).
 */
@Service
@Transactional(readOnly = true)
public class MapaService {

	private static final Logger log = LoggerFactory.getLogger(MapaService.class);

	/** Numero de celulas na largura do viewport quando o mapa agrega. */
	private static final int CELULAS_POR_VIEWPORT = 32;

	private final MapaRepository repositorio;
	private final MapaProperties propriedades;

	public MapaService(MapaRepository repositorio, MapaProperties propriedades) {
		this.repositorio = repositorio;
		this.propriedades = propriedades;
	}

	public ColecaoDeFeicoes consultar(Viewport viewport, int zoom, boolean apenasAtivos) {
		int limite = propriedades.limiteFeicoes();
		boolean agregar = zoom < propriedades.zoomMinimoDetalhe();

		List<Feicao> feicoes = agregar
				? repositorio.clustersNoViewport(viewport.minLon(), viewport.minLat(), viewport.maxLon(),
						viewport.maxLat(), apenasAtivos, tamanhoDaCelula(viewport), limite)
				: repositorio.feicoesNoViewport(viewport.minLon(), viewport.minLat(), viewport.maxLon(),
						viewport.maxLat(), apenasAtivos, limite);

		boolean truncado = !agregar && feicoes.size() >= limite;

		if (truncado) {
			log.debug("Viewport truncado no limite de {} feicoes (zoom {})", limite, zoom);
		}

		return ColecaoDeFeicoes.de(feicoes,
				new ColecaoDeFeicoes.Metadados(zoom, agregar, feicoes.size(), truncado, limite));
	}

	/**
	 * Tamanho da celula de agregacao, em graus.
	 *
	 * <p>Derivado da largura do viewport, e nao do zoom: assim a grade tem sempre
	 * a mesma densidade visual, independentemente do tamanho da janela do mapa.
	 */
	private static double tamanhoDaCelula(Viewport viewport) {
		double largura = viewport.maxLon() - viewport.minLon();
		double celula = largura / CELULAS_POR_VIEWPORT;

		// ST_SnapToGrid com tamanho zero dividiria por zero.
		return celula > 0 ? celula : 0.0001;
	}

	/**
	 * Retangulo do viewport em WGS 84.
	 *
	 * <p>Os valores sao normalizados na construcao: o mapa pode mandar os cantos
	 * invertidos ao arrastar, e latitude/longitude fora da faixa valida quando o
	 * usuario gira o globo. Em vez de recusar, o retangulo e recortado para a
	 * faixa valida — o mapa continua funcionando.
	 */
	public record Viewport(double minLon, double minLat, double maxLon, double maxLat) {

		public static Viewport de(double lon1, double lat1, double lon2, double lat2) {
			double minLon = Math.max(-180, Math.min(lon1, lon2));
			double maxLon = Math.min(180, Math.max(lon1, lon2));
			double minLat = Math.max(-90, Math.min(lat1, lat2));
			double maxLat = Math.min(90, Math.max(lat1, lat2));

			return new Viewport(minLon, minLat, maxLon, maxLat);
		}
	}
}
