package br.com.webgis.imovel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imoveis")
@CrossOrigin(origins = "*")
public class ImovelController {

	@Autowired
	private ImovelService service;

	@GetMapping
	public Object listar() {
		System.out.println("listando imoveis");
		return service.listar();
	}

	@GetMapping("/{id}")
	public Object buscar(@PathVariable String id) {
		return service.buscarPorId(id);
	}

	@PostMapping
	public Object criar(@RequestBody Object corpo) {
		return service.criar(corpo);
	}

	@PutMapping("/{id}")
	public Object atualizar(@PathVariable String id, @RequestBody Object corpo) {
		return service.atualizar(id, corpo);
	}

	@DeleteMapping("/{id}")
	public Object excluir(@PathVariable String id) {
		return service.excluir(id);
	}
}
