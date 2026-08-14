package br.com.webgis.imovel;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.webgis.gis.GeometriaService;
import br.com.webgis.imovel.dto.ImovelFiltro;
import br.com.webgis.imovel.dto.ImovelListItem;
import br.com.webgis.imovel.dto.ImovelRequest;
import br.com.webgis.imovel.dto.ImovelResponse;
import br.com.webgis.imovel.dto.PoligonoGeoJson;
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
	private final ObjectMapper json;

	public ImovelService(ImovelRepository repositorio, ProprietarioService proprietarios,
			GeometriaService geometrias, ObjectMapper json) {
		this.repositorio = repositorio;
		this.proprietarios = proprietarios;
		this.geometrias = geometrias;
		this.json = json;
	}

	public Page<ImovelListItem> listar(ImovelFiltro filtro, Pageable paginacao) {
		return repositorio.findAll(ImovelSpecs.de(filtro), paginacao)
				.map(ImovelMapper::paraListItem);
	}

	public ImovelResponse buscar(Long id) {
		Imovel imovel = buscarEntidade(id);

		// O detalhe carrega a geometria para a tela de edicao poder redesenhar o
		// lote exatamente como esta gravado. A listagem continua sem geometria.
		String geometria = geometrias.geoJsonDoImovel(id);

		return ImovelMapper.paraResponse(imovel, geometria != null, geometria);
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
	 * Sincroniza o poligono com a forma informada.
	 *
	 * <p>Duas formas de definir o lote, uma so regra de conflito: as duas passam
	 * pelo mesmo advisory lock e pela mesma checagem de interseccao, entao um
	 * desenho e um retangulo disputando a mesma area se enxergam.
	 *
	 * @return {@code true} se o imovel ficou com geometria gravada
	 */
	private boolean aplicarGeometria(Imovel imovel, DadosDoImovel dados) {
		if (dados.temDimensoes()) {
			geometrias.aplicarGeometria(imovel.getId(), dados.latitude(), dados.longitude(),
					dados.larguraM(), dados.comprimentoM());
			return true;
		}

		if (dados.temPoligonoDesenhado()) {
			var analise = geometrias.aplicarPoligono(imovel.getId(), serializar(dados.poligono()));

			// Area e ponto passam a ser derivados do desenho.
			imovel.sincronizarComPoligono(
					BigDecimal.valueOf(analise.areaM2()).setScale(2, RoundingMode.HALF_UP),
					BigDecimal.valueOf(analise.latitude()).setScale(7, RoundingMode.HALF_UP),
					BigDecimal.valueOf(analise.longitude()).setScale(7, RoundingMode.HALF_UP));

			repositorio.saveAndFlush(imovel);
			return true;
		}

		// Imovel que perdeu a geometria volta a ser apenas ponto. Sem isso, o
		// poligono antigo continuaria bloqueando a area no banco.
		geometrias.removerGeometria(imovel.getId());
		return false;
	}

	private String serializar(PoligonoGeoJson poligono) {
		try {
			return json.writeValueAsString(poligono);
		} catch (JsonProcessingException e) {
			// A Bean Validation ja garantiu a forma; chegar aqui e bug, nao entrada ruim.
			throw new IllegalStateException("Nao foi possivel serializar o poligono desenhado", e);
		}
	}
}
