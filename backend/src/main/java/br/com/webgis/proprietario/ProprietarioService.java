package br.com.webgis.proprietario;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.webgis.proprietario.dto.ProprietarioListItem;
import br.com.webgis.proprietario.dto.ProprietarioResponse;
import br.com.webgis.shared.error.ConflitoDeDadosException;
import br.com.webgis.shared.error.RecursoNaoEncontradoException;

/**
 * Regras de proprietario.
 *
 * <p>Leitura por padrao ({@code readOnly = true}); so os metodos que alteram
 * abrem transacao de escrita. No codigo original, {@code @Transactional} estava
 * na classe inteira, o que abria transacao de escrita ate para listar.
 */
@Service
@Transactional(readOnly = true)
public class ProprietarioService {

	private static final Logger log = LoggerFactory.getLogger(ProprietarioService.class);

	private final ProprietarioRepository repositorio;

	public ProprietarioService(ProprietarioRepository repositorio) {
		this.repositorio = repositorio;
	}

	public Page<ProprietarioListItem> listar(String busca, Pageable paginacao) {
		return repositorio.listar(padraoDeBusca(busca), paginacao);
	}

	public ProprietarioResponse buscar(Long id) {
		Proprietario proprietario = buscarEntidade(id);
		return paraResponse(proprietario, repositorio.contarImoveis(id));
	}

	public Proprietario buscarEntidade(Long id) {
		return repositorio.findById(id)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Proprietario", id));
	}

	@Transactional
	public ProprietarioResponse criar(String nome) {
		Proprietario proprietario = new Proprietario(nome);
		garantirNomeDisponivel(proprietario.getNomeNormalizado(), null);

		Proprietario salvo = salvarTraduzindoConflito(proprietario);
		log.info("Proprietario criado: id={}", salvo.getId());

		return paraResponse(salvo, 0);
	}

	/**
	 * Renomeia o titular.
	 *
	 * <p>Requisito 5 do desafio: como os imoveis apontam para o id, alterar o nome
	 * aqui vale automaticamente para <strong>todos</strong> os imoveis dele — uma
	 * linha alterada, nenhum {@code UPDATE} em massa e nenhuma chance de
	 * atualizacao parcial.
	 */
	@Transactional
	public ProprietarioResponse renomear(Long id, String novoNome) {
		Proprietario proprietario = buscarEntidade(id);

		String nomeAnterior = proprietario.getNome();
		proprietario.renomear(novoNome);

		garantirNomeDisponivel(proprietario.getNomeNormalizado(), id);

		Proprietario salvo = salvarTraduzindoConflito(proprietario);
		long imoveis = repositorio.contarImoveis(id);

		log.info("Proprietario {} renomeado, {} imovel(is) afetado(s) pela mudanca", id, imoveis);

		if (!nomeAnterior.equals(salvo.getNome())) {
			log.debug("Renomeacao aplicada ao proprietario {}", id);
		}

		return paraResponse(salvo, imoveis);
	}

	@Transactional
	public void excluir(Long id) {
		Proprietario proprietario = buscarEntidade(id);
		long imoveis = repositorio.contarImoveis(id);

		if (imoveis > 0) {
			throw new ConflitoDeDadosException(
					"O proprietario possui %d imovel(is) e nao pode ser excluido.".formatted(imoveis));
		}

		repositorio.delete(proprietario);
		log.info("Proprietario excluido: id={}", id);
	}

	/**
	 * Localiza o titular pelo nome normalizado ou cria um novo.
	 *
	 * <p>E o ponto de entrada usado pelo cadastro de imovel, que recebe o nome
	 * digitado. Mantem o fluxo original ("digite o proprietario") sem abrir mao da
	 * entidade: dois imoveis com o mesmo nome apontam para a mesma linha.
	 */
	@Transactional
	public Proprietario localizarOuCriar(String nome) {
		String normalizado = NomeNormalizador.normalizar(nome);

		return repositorio.findByNomeNormalizado(normalizado)
				.orElseGet(() -> salvarTraduzindoConflito(new Proprietario(nome)));
	}

	private void garantirNomeDisponivel(String nomeNormalizado, Long idAtual) {
		boolean emUso = idAtual == null
				? repositorio.findByNomeNormalizado(nomeNormalizado).isPresent()
				: repositorio.existsByNomeNormalizadoAndIdNot(nomeNormalizado, idAtual);

		if (emUso) {
			throw new ConflitoDeDadosException("Ja existe um proprietario com esse nome.");
		}
	}

	/**
	 * A checagem acima resolve o caso comum, mas duas requisicoes simultaneas
	 * podem passar por ela juntas. A constraint unica do banco e quem decide de
	 * fato; aqui a violacao vira 409 em vez de 500.
	 */
	private Proprietario salvarTraduzindoConflito(Proprietario proprietario) {
		try {
			return repositorio.saveAndFlush(proprietario);
		} catch (DataIntegrityViolationException e) {
			log.warn("Conflito de unicidade ao gravar proprietario '{}'", proprietario.getNome(), e);
			throw new ConflitoDeDadosException("Ja existe um proprietario com esse nome.", e);
		}
	}

	private static ProprietarioResponse paraResponse(Proprietario proprietario, long quantidadeImoveis) {
		return new ProprietarioResponse(
				proprietario.getId(),
				proprietario.getNome(),
				quantidadeImoveis,
				proprietario.getCriadoEm(),
				proprietario.getAtualizadoEm());
	}

	/** Converte a busca em padrao LIKE. Sem busca, casa com tudo. */
	private static String padraoDeBusca(String busca) {
		if (busca == null || busca.isBlank()) {
			return "%";
		}
		String escapado = busca.trim().toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + escapado + "%";
	}
}
