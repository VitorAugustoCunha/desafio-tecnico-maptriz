package br.com.webgis.imovel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import br.com.webgis.imovel.dto.ImovelListItem;
import br.com.webgis.imovel.dto.ImovelRequest;
import br.com.webgis.imovel.dto.ImovelResponse;

/**
 * Conversao entre o contrato HTTP e o dominio.
 *
 * <p>Concentra a normalizacao para que ela aconteça uma vez so e do mesmo jeito
 * em todos os caminhos de escrita: no codigo original, o valor ia do JSON direto
 * para a SQL sem passar por lugar nenhum.
 */
public final class ImovelMapper {

	private ImovelMapper() {
	}

	public static DadosDoImovel paraDados(ImovelRequest request) {
		return new DadosDoImovel(
				texto(request.municipio()),
				request.uf().trim().toUpperCase(Locale.ROOT),
				texto(request.bairro()),
				texto(request.rua()),
				texto(request.numero()),
				request.latitude(),
				request.longitude(),
				areaDe(request),
				request.larguraM(),
				request.comprimentoM(),
				request.geometria(),
				Boolean.TRUE.equals(request.ativo()));
	}

	/**
	 * Area do lote.
	 *
	 * <p>Quando o cadastro informa largura e comprimento, a area passa a ser
	 * <strong>derivada</strong> delas e o valor enviado em {@code areaM2} e
	 * ignorado. Guardar uma area que contradiz o poligono gravado seria manter
	 * duas versoes da mesma verdade — e a que o mapa mostra e a do poligono.
	 */
	static BigDecimal areaDe(ImovelRequest request) {
		if (request.possuiDimensoes()) {
			return request.larguraM().multiply(request.comprimentoM()).setScale(2, RoundingMode.HALF_UP);
		}
		if (request.possuiPoligonoDesenhado()) {
			// Provisorio: o valor definitivo vem do ST_Area do poligono gravado.
			// Aqui basta um numero positivo para satisfazer o NOT NULL da coluna
			// no INSERT que precede a gravacao da geometria.
			return request.areaM2() == null ? BigDecimal.ONE : request.areaM2();
		}
		return request.areaM2();
	}

	public static ImovelResponse paraResponse(Imovel imovel, boolean possuiGeometria) {
		return paraResponse(imovel, possuiGeometria, null);
	}

	public static ImovelResponse paraResponse(Imovel imovel, boolean possuiGeometria, String geometriaGeoJson) {
		return new ImovelResponse(
				imovel.getId(),
				new ImovelResponse.ProprietarioResumo(
						imovel.getProprietario().getId(),
						imovel.getProprietario().getNome()),
				imovel.getMunicipio(),
				imovel.getUf(),
				imovel.getBairro(),
				imovel.getRua(),
				imovel.getNumero(),
				imovel.getLatitude(),
				imovel.getLongitude(),
				imovel.getAreaM2(),
				imovel.getLarguraM(),
				imovel.getComprimentoM(),
				possuiGeometria,
				geometriaGeoJson,
				imovel.isAtivo(),
				imovel.getCriadoEm(),
				imovel.getAtualizadoEm());
	}

	public static ImovelListItem paraListItem(Imovel imovel) {
		return new ImovelListItem(
				imovel.getId(),
				imovel.getProprietario().getId(),
				imovel.getProprietario().getNome(),
				imovel.getMunicipio(),
				imovel.getUf(),
				imovel.getBairro(),
				imovel.getRua(),
				imovel.getNumero(),
				imovel.getLatitude(),
				imovel.getLongitude(),
				imovel.getAreaM2(),
				imovel.isAtivo());
	}

	private static String texto(String valor) {
		return valor.trim().replaceAll("\\s+", " ");
	}
}
