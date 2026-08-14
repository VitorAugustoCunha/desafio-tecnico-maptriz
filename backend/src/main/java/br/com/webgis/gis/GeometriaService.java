package br.com.webgis.gis;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.webgis.imovel.ImovelRepository;
import br.com.webgis.shared.error.ConflitoEspacialException;

/**
 * Geracao e validacao do poligono do lote (tarefa 8).
 *
 * <p><b>Como o conflito e evitado sem race condition.</b> "Consultar se ha
 * sobreposicao" e "gravar a geometria" sao dois passos; entre um e outro, outra
 * transacao pode inserir um lote no mesmo lugar. Em READ COMMITTED, nenhuma das
 * duas enxerga a outra, e as duas gravam.
 *
 * <p>A solucao aqui e um <b>advisory lock de transacao</b>
 * ({@code pg_advisory_xact_lock}) tomado antes da verificacao e liberado
 * automaticamente no commit ou rollback. Quem chegar depois espera, e ao acordar
 * ja enxerga a geometria commitada pelo primeiro — entao encontra o conflito e
 * recebe {@code 409}.
 *
 * <p><b>Trade-off assumido:</b> a chave do lock e unica para todo o cadastro,
 * entao as escritas <i>com geometria</i> sao serializadas. Para volume de
 * cadastro imobiliario (escrita rara, leitura intensa) isso e irrelevante, e a
 * leitura nao e afetada — nenhuma consulta pega esse lock. Se a escrita virasse
 * gargalo, o proximo passo seria uma chave por celula de grade espacial,
 * travando todas as celulas tocadas pelo retangulo em ordem determinista para
 * nao criar deadlock. Nao fiz isso agora porque seria complexidade sem problema
 * medido. Ver docs/DECISIONS.md, ADR-005.
 *
 * <p>A alternativa "constraint de exclusao" ({@code EXCLUDE USING gist}) resolveria
 * no proprio banco, mas so cobre a insercao de uma geometria por linha e daria
 * uma mensagem de erro sem o id do imovel conflitante, que a interface precisa.
 */
@Service
public class GeometriaService {

	private static final Logger log = LoggerFactory.getLogger(GeometriaService.class);

	private final GeometriaRepository geometrias;
	private final ImovelRepository imoveis;

	public GeometriaService(GeometriaRepository geometrias, ImovelRepository imoveis) {
		this.geometrias = geometrias;
		this.imoveis = imoveis;
	}

	/**
	 * Verifica e grava a geometria do imovel.
	 *
	 * <p>Exige transacao ativa: a checagem e a gravacao precisam estar na mesma
	 * unidade atomica do INSERT/UPDATE do imovel, senao o lock nao protege nada.
	 *
	 * @throws ConflitoEspacialException se o retangulo intersecta outro imovel
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void aplicarGeometria(Long idImovel, BigDecimal latitude, BigDecimal longitude,
			BigDecimal larguraM, BigDecimal comprimentoM) {

		geometrias.bloquearEscritaGeometrica();

		long idAtual = idImovel == null ? GeometriaRepository.SEM_IMOVEL_ATUAL : idImovel;

		GeometriaRepository.ResultadoVerificacao resultado =
				geometrias.verificar(idAtual, longitude, latitude, larguraM, comprimentoM);

		if (!resultado.geometriaValida()) {
			throw new IllegalArgumentException(
					"As coordenadas e dimensoes informadas nao produzem um poligono valido.");
		}

		if (resultado.temConflito()) {
			log.info("Cadastro do imovel {} recusado: area conflita com o imovel {}",
					idImovel, resultado.idImovelConflitante());
			throw new ConflitoEspacialException(resultado.idImovelConflitante());
		}

		int atualizados = imoveis.gravarGeometria(idImovel, longitude.doubleValue(), latitude.doubleValue(),
				larguraM.doubleValue(), comprimentoM.doubleValue());

		log.debug("Geometria gravada para o imovel {} ({} linha(s))", idImovel, atualizados);
	}

	/** Remove o poligono quando o imovel deixa de ter dimensoes (volta a ser so ponto). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void removerGeometria(Long idImovel) {
		imoveis.limparGeometria(idImovel);
		log.debug("Geometria removida do imovel {}", idImovel);
	}

	public boolean possuiGeometria(Long idImovel) {
		return imoveis.possuiGeometria(idImovel).orElse(false);
	}
}
