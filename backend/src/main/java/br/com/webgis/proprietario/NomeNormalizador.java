package br.com.webgis.proprietario;

import java.util.Locale;

/**
 * Regra de normalizacao de nome usada para deduplicar proprietarios.
 *
 * <p>Precisa produzir exatamente o mesmo resultado que a funcao SQL
 * {@code webgis_normaliza_nome} criada na migration {@code V3__proprietario.sql}:
 * se as duas divergirem, a aplicacao aceita um nome que a constraint unica do
 * banco depois rejeita (ou pior, o contrario).
 *
 * <p>A regra e: colapsa espacos internos, recorta as pontas, passa para
 * minusculas. Acentos sao <strong>preservados</strong> de proposito — unir dois
 * titulares distintos e um erro irreversivel no cadastro, enquanto manter
 * 'Jose' e 'José' separados e corrigivel por uma fusao explicita depois.
 */
public final class NomeNormalizador {

	private NomeNormalizador() {
	}

	/** Chave de deduplicacao. Equivalente a {@code lower(btrim(regexp_replace(n, '\s+', ' ', 'g')))}. */
	public static String normalizar(String nome) {
		return exibicao(nome).toLowerCase(Locale.ROOT);
	}

	/** Forma canonica para exibicao: mesmo tratamento de espacos, sem mudar a caixa. */
	public static String exibicao(String nome) {
		if (nome == null) {
			throw new IllegalArgumentException("nome nao pode ser nulo");
		}
		return nome.replaceAll("\\s+", " ").trim();
	}
}
