package br.com.webgis.imovel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.webgis.gis.GeometriaService;
import br.com.webgis.imovel.dto.ImovelFiltro;
import br.com.webgis.imovel.dto.ImovelListItem;
import br.com.webgis.imovel.dto.ImovelRequest;
import br.com.webgis.imovel.dto.ImovelResponse;
import br.com.webgis.proprietario.Proprietario;
import br.com.webgis.proprietario.ProprietarioService;
import br.com.webgis.shared.error.RecursoNaoEncontradoException;

/**
 * Regras do imovel.
 *
 * <p>A validacao de sobreposicao e a gravacao acontecem <strong>na mesma
 * transacao sincrona</strong> da escrita. Consistencia espacial e regra de
 * negocio critica: mandar isso para processamento eventual aceitaria, ainda que
 * por instantes, dois lotes no mesmo lugar. O pool de trabalho GIS existe para
 * exportacao em lote, nao para isto (ver docs/DECISIONS.md, ADR-006).
 */
@Service
@Transactional(readOnly = true)
public class ImovelService {

	private static final Logger log = LoggerFactory.getLogger(ImovelService.class);

	private final ImovelRepository repositorio;
	private final ProprietarioService proprietarios;
	private final GeometriaService geometrias;

	public ImovelService(ImovelRepository repositorio, ProprietarioService proprietarios,
			GeometriaService geometrias) {
		this.repositorio = repositorio;
		this.proprietarios = proprietarios;
		this.geometrias = geometrias;
	}

	public Page<ImovelListItem> listar(ImovelFiltro filtro, Pageable paginacao) {
		return repositorio.findAll(ImovelSpecs.de(filtro), paginacao)
				.map(ImovelMapper::paraListItem);
	}

	public ImovelResponse buscar(Long id) {
		Imovel imovel = buscarEntidade(id);
		return ImovelMapper.paraResponse(imovel, geometrias.possuiGeometria(id));
	}

	@Transactional
	public ImovelResponse criar(ImovelRequest request) {
		Proprietario proprietario = proprietarios.localizarOuCriar(request.proprietarioNome());
		DadosDoImovel dados = ImovelMapper.paraDados(request);

		Imovel imovel = new Imovel(proprietario, dados);

		// Precisa do id antes de gravar a geometria, e o INSERT precisa estar
		// visivel para a propria transacao na checagem de conflito.
		repositorio.saveAndFlush(imovel);

		boolean comGeometria = aplicarGeometria(imovel, dados);

		log.info("Imovel criado: id={}, proprietario={}, comGeometria={}",
				imovel.getId(), proprietario.getId(), comGeometria);

		return ImovelMapper.paraResponse(imovel, comGeometria);
	}

	@Transactional
	public ImovelResponse atualizar(Long id, ImovelRequest request) {
		Imovel imovel = buscarEntidade(id);

		Proprietario proprietario = proprietarios.localizarOuCriar(request.proprietarioNome());
		DadosDoImovel dados = ImovelMapper.paraDados(request);

		imovel.trocarProprietario(proprietario);
		imovel.aplicar(dados);

		repositorio.saveAndFlush(imovel);

		boolean comGeometria = aplicarGeometria(imovel, dados);

		log.info("Imovel atualizado: id={}, comGeometria={}", id, comGeometria);

		return ImovelMapper.paraResponse(imovel, comGeometria);
	}

	@Transactional
	public void excluir(Long id) {
		Imovel imovel = buscarEntidade(id);
		repositorio.delete(imovel);
		log.info("Imovel excluido: id={}", id);
	}

	private Imovel buscarEntidade(Long id) {
		return repositorio.findWithProprietarioById(id)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Imovel", id));
	}

	/**
	 * Sincroniza o poligono com as dimensoes informadas.
	 *
	 * @return {@code true} se o imovel ficou com geometria gravada
	 */
	private boolean aplicarGeometria(Imovel imovel, DadosDoImovel dados) {
		boolean temDimensoes = dados.larguraM() != null && dados.comprimentoM() != null;

		if (temDimensoes) {
			geometrias.aplicarGeometria(imovel.getId(), dados.latitude(), dados.longitude(),
					dados.larguraM(), dados.comprimentoM());
			return true;
		}

		// Imovel que perdeu as dimensoes volta a ser apenas ponto. Sem isso, o
		// poligono antigo continuaria bloqueando a area no banco.
		geometrias.removerGeometria(imovel.getId());
		return false;
	}
}
