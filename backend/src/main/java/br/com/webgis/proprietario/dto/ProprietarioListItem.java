package br.com.webgis.proprietario.dto;

/**
 * Linha da listagem de proprietarios.
 *
 * <p>Record de topo (e nao aninhado) porque e referenciado por nome dentro de uma
 * consulta JPQL com {@code SELECT new}.
 *
 * <p>{@code quantidadeImoveis} vem agregado na propria consulta: calcular no laco
 * da aplicacao seria um SELECT por proprietario (N+1).
 */
public record ProprietarioListItem(Long id, String nome, long quantidadeImoveis) {
}
