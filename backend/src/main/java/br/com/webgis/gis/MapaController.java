package br.com.webgis.gis;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.webgis.gis.dto.ColecaoDeFeicoes;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Endpoint de mapa (tarefa 7).
 *
 * <p>Separado de {@code /api/imoveis} de proposito: o mapa precisa de geometria e
 * de recorte espacial, a listagem precisa de paginacao e endereco. Misturar os
 * dois faria cada um carregar o peso do outro.
 */
@RestController
@RequestMapping("/api/mapa")
public class MapaController {

	private final MapaService servico;

	public MapaController(MapaService servico) {
		this.servico = servico;
	}

	/**
	 * Feicoes dentro do viewport, em GeoJSON.
	 *
	 * <p>O bbox e obrigatorio: nao ha como pedir "todos os imoveis" por aqui.
	 */
	@GetMapping(value = "/imoveis", produces = MediaType.APPLICATION_JSON_VALUE)
	public ColecaoDeFeicoes noViewport(
			@RequestParam @DecimalMin("-180") @DecimalMax("180") double minLon,
			@RequestParam @DecimalMin("-90") @DecimalMax("90") double minLat,
			@RequestParam @DecimalMin("-180") @DecimalMax("180") double maxLon,
			@RequestParam @DecimalMin("-90") @DecimalMax("90") double maxLat,
			@RequestParam(defaultValue = "12") @Min(0) @Max(22) int zoom,
			@RequestParam(defaultValue = "true") boolean apenasAtivos) {

		MapaService.Viewport viewport = MapaService.Viewport.de(minLon, minLat, maxLon, maxLat);

		return servico.consultar(viewport, zoom, apenasAtivos);
	}
}
