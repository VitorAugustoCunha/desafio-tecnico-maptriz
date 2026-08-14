package br.com.webgis.gis.dto;

/**
 * Propriedades de uma feicao GeoJSON.
 *
 * <p>Interface selada em vez de {@code Map<String, Object>}: o cliente sabe
 * exatamente quais campos existem em cada caso, e o compilador cobra a
 * atualizacao dos dois lados quando o contrato muda.
 */
public sealed interface PropriedadesFeicao permits PropriedadesImovel, PropriedadesCluster {
}
