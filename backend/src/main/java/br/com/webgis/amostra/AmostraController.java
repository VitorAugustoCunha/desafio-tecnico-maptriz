package br.com.webgis.amostra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.webgis.amostra.dto.AmostraResponse;
import jakarta.validation.constraints.Min;

/**
 * Geracao de massa de teste.
 *
 * <p>Fica fora de {@code /api/imoveis} de proposito: nao e uma operacao do
 * cadastro, e uma ferramenta para popular o mapa e a listagem com volume
 * suficiente para exercitar paginacao, clusterizacao e exportacao.
 *
 * <p>O bean so e registrado com {@code webgis.amostra.habilitada=true}. Com a
 * chave desligada o caminho responde {@code 404} — nao existe endpoint de
 * escrita em massa disponivel e recusando.
 */
@RestController
@RequestMapping("/api/amostra")
@ConditionalOnProperty(prefix = "webgis.amostra", name = "habilitada", havingValue = "true", matchIfMissing = true)
public class AmostraController {

	private final AmostraService servico;

	public AmostraController(AmostraService servico) {
		this.servico = servico;
	}

	/** {@code 201} com o que foi criado — inclusive quantos lotes foram pulados por conflito. */
	@PostMapping("/imoveis")
	public ResponseEntity<AmostraResponse> gerar(
			@RequestParam(required = false) @Min(value = 1, message = "a quantidade deve ser maior que zero")
			Integer quantidade) {

		return ResponseEntity.status(201).body(servico.gerar(quantidade));
	}
}
