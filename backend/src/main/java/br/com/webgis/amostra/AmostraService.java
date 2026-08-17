package br.com.webgis.amostra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.webgis.amostra.dto.AmostraResponse;
import br.com.webgis.gis.GeometriaRepository;
import br.com.webgis.proprietario.Proprietario;
import br.com.webgis.proprietario.ProprietarioService;

/**
 * Geracao de massa de teste: uma quadra de lotes que nao se sobrepoem.
 *
 * <h3>Por que uma grade, e nao pontos aleatorios</h3>
 *
 * <p>O cadastro recusa lotes que se intersectam. Sorteio uniforme numa area
 * pequena colidiria o tempo todo e a carga viraria uma sequencia de recusas;
 * espalhar por todo o Brasil resolveria a colisao mas cairia fora da zona UTM
 * 22S, onde a projecao do projeto distorce (ver docs/DECISIONS.md, ADR-004).
 *
 * <p>A grade resolve os dois: cada lote fica no centro de uma celula maior que
 * ele, entao a nao sobreposicao e garantida por construcao — nao por sorte — e
 * o conjunto todo cabe numa area de poucos quilometros dentro da zona 22S.
 *
 * <h3>Dimensoes</h3>
 *
 * <p>Todo lote sai com largura e comprimento preenchidos, variando por dois
 * ciclos de tamanhos primos entre si (7 e 11) para os 77 formatos possiveis nao
 * se repetirem em bloco. Area e poligono vem dai, pelo mesmo caminho do
 * cadastro manual.
 */
@Service
public class AmostraService {

	private static final Logger log = LoggerFactory.getLogger(AmostraService.class);

	/** Bauru/SP: bem dentro da zona UTM 22S, onde o EPSG:31982 nao distorce. */
	private static final double LATITUDE_BASE = -22.3200000;
	private static final double LONGITUDE_BASE = -49.0700000;

	private static final String MUNICIPIO = "Bauru";
	private static final String UF = "SP";

	/** Bairro marcador: e por ele que a amostra e localizada, contada e filtrada na tela. */
	static final String BAIRRO = "Distrito Amostra";

	/**
	 * Passo da grade, em metros.
	 *
	 * <p>Tem que ser maior que o maior lote possivel (24 m x 40 m), senao lotes
	 * vizinhos encostariam — e encostar ja conta como conflito, porque a regra usa
	 * {@code ST_Intersects} e nao {@code ST_Overlaps}.
	 */
	private static final double PASSO_X = 40;
	private static final double PASSO_Y = 60;

	/**
	 * Largura da grade em celulas.
	 *
	 * <p>Constante de proposito, e nao derivada da quantidade: e o que permite
	 * empilhar blocos de cargas sucessivas em linhas alinhadas, sem recalcular
	 * onde o bloco anterior terminou.
	 */
	private static final int COLUNAS = 40;

	/** Titulares ficticios em rodizio — a listagem e o filtro por proprietario ganham volume. */
	private static final int TITULARES = 25;

	private final AmostraRepository repositorio;
	private final ProprietarioService proprietarios;
	private final GeometriaRepository geometrias;
	private final AmostraProperties propriedades;

	public AmostraService(AmostraRepository repositorio, ProprietarioService proprietarios,
			GeometriaRepository geometrias, AmostraProperties propriedades) {
		this.repositorio = repositorio;
		this.proprietarios = proprietarios;
		this.geometrias = geometrias;
		this.propriedades = propriedades;
	}

	/**
	 * Gera lotes de amostra.
	 *
	 * <p>Toma o <b>mesmo</b> advisory lock das demais escritas com geometria: sem
	 * isso, um cadastro manual simultaneo poderia gravar um lote no meio da grade
	 * sem que nenhum dos dois enxergasse o outro.
	 */
	@Transactional
	public AmostraResponse gerar(Integer quantidadePedida) {
		int quantidade = propriedades.ajustar(quantidadePedida);

		geometrias.bloquearEscritaGeometrica();

		String titulares = idsDosTitulares();

		// Blocos sucessivos empilham para o norte, com uma linha vazia entre eles.
		long jaExistem = repositorio.contar(MUNICIPIO, BAIRRO);
		int linhaInicial = jaExistem == 0 ? 0 : (int) Math.ceilDiv(jaExistem, COLUNAS) + 1;

		int criados = repositorio.inserirGrade(titulares, quantidade, linhaInicial, COLUNAS,
				PASSO_X, PASSO_Y, LATITUDE_BASE, LONGITUDE_BASE, MUNICIPIO, UF, BAIRRO);

		AmostraRepository.Centro centro = repositorio.centro(MUNICIPIO, BAIRRO);

		log.info("Amostra gerada: {} de {} lote(s) criados a partir da linha {} ({} ja existiam)",
				criados, quantidade, linhaInicial, jaExistem);

		return new AmostraResponse(
				quantidade,
				criados,
				quantidade - criados,
				MUNICIPIO,
				BAIRRO,
				arredondar(centro == null ? LATITUDE_BASE : centro.latitude()),
				arredondar(centro == null ? LONGITUDE_BASE : centro.longitude()));
	}

	/**
	 * Ids dos titulares ficticios, em texto separado por virgula.
	 *
	 * <p>Passar a lista como parametro unico evita tanto concatenar SQL quanto
	 * depender de binding de array do driver.
	 */
	private String idsDosTitulares() {
		List<Long> ids = new ArrayList<>(TITULARES);

		for (int i = 1; i <= TITULARES; i++) {
			Proprietario titular = proprietarios.localizarOuCriar("Titular de Amostra %02d".formatted(i));
			ids.add(titular.getId());
		}

		return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	private static BigDecimal arredondar(double valor) {
		return BigDecimal.valueOf(valor).setScale(7, RoundingMode.HALF_UP);
	}
}
