package br.com.webgis.gis.worker;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.webgis.shared.error.RecursoNaoEncontradoException;
import jakarta.validation.Valid;

/**
 * Exportacao GeoJSON em lote — o caso de uso do pool de trabalho GIS.
 *
 * <p>Fluxo assincrono classico: {@code POST} aceita e devolve {@code 202} com o
 * {@code Location} de onde acompanhar; o cliente consulta o status; quando
 * concluida, baixa o arquivo. Se o pool estiver saturado, o {@code POST} devolve
 * {@code 503} com {@code Retry-After}.
 */
@RestController
@RequestMapping("/api/exportacoes")
public class ExportacaoController {

	private final ExportacaoGeoJsonService servico;

	public ExportacaoController(ExportacaoGeoJsonService servico) {
		this.servico = servico;
	}

	@PostMapping("/geojson")
	public ResponseEntity<ExportacaoResponse> solicitar(@RequestBody @Valid FiltroExportacao filtro,
			UriComponentsBuilder uriBuilder) {

		Exportacao exportacao = servico.submeter(filtro);

		URI acompanhamento = uriBuilder.path("/api/exportacoes/{id}")
				.buildAndExpand(exportacao.getId()).toUri();

		return ResponseEntity.accepted()
				.location(acompanhamento)
				.body(ExportacaoResponse.de(exportacao));
	}

	@GetMapping("/{id}")
	public ExportacaoResponse consultar(@PathVariable UUID id) {
		return ExportacaoResponse.de(servico.buscar(id));
	}

	@GetMapping("/{id}/arquivo")
	public ResponseEntity<Resource> baixar(@PathVariable UUID id) throws IOException {
		Exportacao exportacao = servico.buscar(id);

		if (exportacao.getStatus() != ExportacaoStatus.CONCLUIDA) {
			throw new RecursoNaoEncontradoException("Arquivo da exportacao", id);
		}

		Path caminho = exportacao.getArquivo();
		if (caminho == null || !Files.exists(caminho)) {
			throw new RecursoNaoEncontradoException("Arquivo da exportacao", id);
		}

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.contentLength(Files.size(caminho))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"imoveis-%s.geojson\"".formatted(id))
				.body(new FileSystemResource(caminho));
	}

	/** Cancelamento logico: o worker interrompe entre um lote e outro. */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
		servico.cancelar(id);
		return ResponseEntity.noContent().build();
	}
}
