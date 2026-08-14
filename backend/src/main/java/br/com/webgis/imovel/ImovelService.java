package br.com.webgis.imovel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ImovelService {

	@Autowired
	private EntityManager em;

	private static final String COLUNAS =
			"id, proprietario, municipio, uf, bairro, rua, numero, latitude, longitude, area_m2, ativo, criado_em, atualizado_em";

	public Object listar() {
		String sql = "select " + COLUNAS + " from imovel order by proprietario";

		System.out.println("SQL: " + sql);

		Query query = em.createNativeQuery(sql);
		List linhas = query.getResultList();

		List resultado = new ArrayList();
		for (int i = 0; i < linhas.size(); i++) {
			resultado.add(montarImovel((Object[]) linhas.get(i)));
		}

		return resultado;
	}

	public Object buscarPorId(String id) {
		Query query = em.createNativeQuery("select " + COLUNAS + " from imovel where id = " + id);
		List linhas = query.getResultList();

		if (linhas.size() == 0) {
			return null;
		}

		return montarImovel((Object[]) linhas.get(0));
	}

	public Object criar(Object corpo) {
		try {
			Map m = (Map) corpo;

			String sql = "insert into imovel (proprietario, municipio, uf, bairro, rua, numero, latitude, longitude, area_m2, ativo, criado_em, atualizado_em) values ('"
					+ m.get("proprietario") + "', '"
					+ m.get("municipio") + "', '"
					+ m.get("uf") + "', '"
					+ m.get("bairro") + "', '"
					+ m.get("rua") + "', '"
					+ m.get("numero") + "', "
					+ m.get("latitude") + ", "
					+ m.get("longitude") + ", "
					+ m.get("areaM2") + ", "
					+ m.get("ativo") + ", now(), now())";

			System.out.println("SQL: " + sql);
			em.createNativeQuery(sql).executeUpdate();

			HashMap retorno = new HashMap();
			retorno.put("status", "ok");
			return retorno;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public Object atualizar(String id, Object corpo) {
		try {
			Map m = (Map) corpo;

			String sql = "update imovel set "
					+ "proprietario = '" + m.get("proprietario") + "', "
					+ "municipio = '" + m.get("municipio") + "', "
					+ "uf = '" + m.get("uf") + "', "
					+ "bairro = '" + m.get("bairro") + "', "
					+ "rua = '" + m.get("rua") + "', "
					+ "numero = '" + m.get("numero") + "', "
					+ "latitude = " + m.get("latitude") + ", "
					+ "longitude = " + m.get("longitude") + ", "
					+ "area_m2 = " + m.get("areaM2") + ", "
					+ "ativo = " + m.get("ativo") + ", "
					+ "atualizado_em = now() "
					+ "where id = " + id;

			System.out.println("SQL: " + sql);
			em.createNativeQuery(sql).executeUpdate();

			HashMap retorno = new HashMap();
			retorno.put("status", "ok");
			return retorno;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public Object excluir(String id) {
		em.createNativeQuery("delete from imovel where id = " + id).executeUpdate();

		HashMap retorno = new HashMap();
		retorno.put("status", "ok");
		return retorno;
	}

	private HashMap montarImovel(Object[] linha) {
		HashMap item = new HashMap();
		item.put("id", linha[0]);
		item.put("proprietario", linha[1]);
		item.put("municipio", linha[2]);
		item.put("uf", linha[3]);
		item.put("bairro", linha[4]);
		item.put("rua", linha[5]);
		item.put("numero", linha[6]);
		item.put("latitude", linha[7]);
		item.put("longitude", linha[8]);
		item.put("areaM2", linha[9]);
		item.put("ativo", linha[10]);
		item.put("criadoEm", linha[11]);
		item.put("atualizadoEm", linha[12]);
		return item;
	}
}
