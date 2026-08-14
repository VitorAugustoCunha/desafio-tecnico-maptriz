package br.com.webgis;

import java.math.BigDecimal;

/**
 * Corpos JSON reutilizados pelos testes de integracao.
 *
 * <p>Classe de apoio propria em vez de um teste chamar metodo de outro: teste
 * nao deveria depender de outro teste nem para compilar.
 */
public final class CorposDeTeste {

	private CorposDeTeste() {
	}

	/** Imovel valido sem dimensoes — fica so com o ponto, sem poligono. */
	public static String imovel(String proprietario, String municipio, String uf, double area) {
		return """
				{"proprietarioNome":"%s","municipio":"%s","uf":"%s","bairro":"Pinheiros",
				 "rua":"Rua dos Pinheiros","numero":"1245","latitude":-23.5629,"longitude":-46.6944,
				 "areaM2":%s,"ativo":true}
				""".formatted(proprietario, municipio, uf, area);
	}

	/** Imovel em coordenada especifica, sem dimensoes. */
	public static String imovelEm(String proprietario, String municipio, String uf,
			BigDecimal latitude, BigDecimal longitude) {
		return """
				{"proprietarioNome":"%s","municipio":"%s","uf":"%s","bairro":"Centro",
				 "rua":"Rua Central","numero":"100","latitude":%s,"longitude":%s,
				 "areaM2":100,"ativo":true}
				""".formatted(proprietario, municipio, uf, latitude, longitude);
	}

	/** Imovel com largura e comprimento — ganha poligono em EPSG:31982. */
	public static String imovelComDimensoes(String proprietario, BigDecimal latitude, BigDecimal longitude,
			double largura, double comprimento) {
		return """
				{"proprietarioNome":"%s","municipio":"Curitiba","uf":"PR","bairro":"Batel",
				 "rua":"Avenida do Batel","numero":"1560","latitude":%s,"longitude":%s,
				 "larguraM":%s,"comprimentoM":%s,"ativo":true}
				""".formatted(proprietario, latitude, longitude, largura, comprimento);
	}

	/** Mesmo imovel do anterior, mas sem dimensoes: volta a ser apenas ponto. */
	public static String imovelSemDimensoes(String proprietario, BigDecimal latitude, BigDecimal longitude) {
		return """
				{"proprietarioNome":"%s","municipio":"Curitiba","uf":"PR","bairro":"Batel",
				 "rua":"Avenida do Batel","numero":"1560","latitude":%s,"longitude":%s,
				 "areaM2":1000,"ativo":true}
				""".formatted(proprietario, latitude, longitude);
	}
}
