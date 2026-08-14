package br.com.webgis.imovel;

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

import br.com.webgis.imovel.dto.ImovelFiltro;
import br.com.webgis.imovel.dto.ImovelListItem;
import br.com.webgis.imovel.dto.ImovelRequest;
import br.com.webgis.imovel.dto.ImovelResponse;
import br.com.webgis.shared.web.PaginaResponse;
import br.com.webgis.shared.web.PaginacaoProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * API de imoveis.
 *
 * <p>O controller so traduz HTTP: valida a entrada, delega e escolhe o status.
 * Nenhuma regra de negocio mora aqui.
 */
@RestController
@RequestMapping("/api/imoveis")
public class ImovelController {

	private final ImovelService servico;
	private final PaginacaoProperties paginacao;

	public ImovelController(ImovelService servico, PaginacaoProperties paginacao) {
		this.servico = servico;
		this.paginacao = paginacao;
	}

	/**
	 * Listagem paginada e filtrada no servidor.
	 *
	 * <p>Nao existe endpoint que devolva todos os imoveis: o {@code tamanho} e
	 * limitado por configuracao, e {@code ordenarPor} so aceita os valores da
	 * whitelist {@link OrdenacaoImovel}.
	 */
	@GetMapping
	public PaginaResponse<ImovelListItem> listar(
			@RequestParam(required = false) Long proprietarioId,
			@RequestParam(required = false) @Size(max = 120) String proprietarioNome,
			@RequestParam(required = false) @Size(max = 120) String municipio,
			@RequestParam(required = false) Boolean ativo,
			@RequestParam(defaultValue = "0") @Min(0) int pagina,
			@RequestParam(required = false) Integer tamanho,
			@RequestParam(required = false) String ordenarPor,
			@RequestParam(required = false) String direcao) {

		ImovelFiltro filtro = new ImovelFiltro(proprietarioId, proprietarioNome, municipio, ativo);

		Sort ordenacao = OrdenacaoImovel.de(ordenarPor).paraSort(direcaoDe(direcao));
		Pageable requisicao = PageRequest.of(pagina, paginacao.ajustar(tamanho), ordenacao);

		return PaginaResponse.de(servico.listar(filtro, requisicao));
	}

	@GetMapping("/{id}")
	public ImovelResponse buscar(@PathVariable Long id) {
		return servico.buscar(id);
	}

	/** {@code 201} com {@code Location} e o recurso criado — o cliente ja sai sabendo o id. */
	@PostMapping
	public ResponseEntity<ImovelResponse> criar(@RequestBody @Valid ImovelRequest request,
			UriComponentsBuilder uriBuilder) {

		ImovelResponse criado = servico.criar(request);

		URI localizacao = uriBuilder.path("/api/imoveis/{id}").buildAndExpand(criado.id()).toUri();

		return ResponseEntity.created(localizacao).body(criado);
	}

	/** {@code 200} com o estado final — a tela nao precisa recarregar a lista para saber o que ficou gravado. */
	@PutMapping("/{id}")
	public ImovelResponse atualizar(@PathVariable Long id, @RequestBody @Valid ImovelRequest request) {
		return servico.atualizar(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> excluir(@PathVariable Long id) {
		servico.excluir(id);
		return ResponseEntity.noContent().build();
	}

	private static Sort.Direction direcaoDe(String direcao) {
		if (direcao == null || direcao.isBlank()) {
			return Sort.Direction.ASC;
		}
		return Sort.Direction.fromOptionalString(direcao.trim())
				.orElseThrow(() -> new IllegalArgumentException(
						"direcao invalida: '%s'. Use 'asc' ou 'desc'.".formatted(direcao)));
	}
}
