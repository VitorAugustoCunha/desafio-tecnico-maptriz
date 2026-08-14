package br.com.webgis.proprietario;

import java.net.URI;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.webgis.proprietario.dto.ProprietarioListItem;
import br.com.webgis.proprietario.dto.ProprietarioRequest;
import br.com.webgis.proprietario.dto.ProprietarioResponse;
import br.com.webgis.shared.web.PaginaResponse;
import br.com.webgis.shared.web.PaginacaoProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * API de proprietarios (tarefas 4 e 5).
 *
 * <p>Os imoveis de um proprietario <strong>nao</strong> vem embutidos no detalhe:
 * a tela busca em {@code GET /api/imoveis?proprietarioId=...}, que ja e paginado.
 * Devolver a lista inteira aninhada traria de volta o problema de volume que a
 * tarefa 6 pede para resolver, so que em outro endpoint.
 */
@RestController
@RequestMapping("/api/proprietarios")
public class ProprietarioController {

	private final ProprietarioService servico;
	private final PaginacaoProperties paginacao;

	public ProprietarioController(ProprietarioService servico, PaginacaoProperties paginacao) {
		this.servico = servico;
		this.paginacao = paginacao;
	}

	@GetMapping
	public PaginaResponse<ProprietarioListItem> listar(
			@RequestParam(required = false) @Size(max = 120) String busca,
			@RequestParam(defaultValue = "0") @Min(0) int pagina,
			@RequestParam(required = false) Integer tamanho) {

		Pageable requisicao = PageRequest.of(pagina, paginacao.ajustar(tamanho), Sort.by("nome").ascending());

		return PaginaResponse.de(servico.listar(busca, requisicao));
	}

	@GetMapping("/{id}")
	public ProprietarioResponse buscar(@PathVariable Long id) {
		return servico.buscar(id);
	}

	@PostMapping
	public ResponseEntity<ProprietarioResponse> criar(@RequestBody @Valid ProprietarioRequest request,
			UriComponentsBuilder uriBuilder) {

		ProprietarioResponse criado = servico.criar(request.nome());

		URI localizacao = uriBuilder.path("/api/proprietarios/{id}").buildAndExpand(criado.id()).toUri();

		return ResponseEntity.created(localizacao).body(criado);
	}

	/**
	 * Renomeia o titular (requisito 5).
	 *
	 * <p>Uma unica entidade e alterada; todos os imoveis dele passam a exibir o
	 * novo nome porque apontam para o id, nao para o texto.
	 */
	@PutMapping("/{id}")
	public ProprietarioResponse renomear(@PathVariable Long id, @RequestBody @Valid ProprietarioRequest request) {
		return servico.renomear(id, request.nome());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> excluir(@PathVariable Long id) {
		servico.excluir(id);
		return ResponseEntity.noContent().build();
	}
}
