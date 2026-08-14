package br.com.webgis.gis.dto;

/** Agregado de imoveis em uma celula da grade, usado nos zooms mais afastados. */
public record PropriedadesCluster(long quantidade) implements PropriedadesFeicao {
}
