/** Envelope de paginacao devolvido pela API. Espelha `PaginaResponse` do backend. */
export interface Pagina<T> {
  readonly conteudo: readonly T[];
  readonly pagina: number;
  readonly tamanho: number;
  readonly totalDeElementos: number;
  readonly totalDePaginas: number;
  readonly primeira: boolean;
  readonly ultima: boolean;
}

export const PAGINA_VAZIA: Pagina<never> = {
  conteudo: [],
  pagina: 0,
  tamanho: 20,
  totalDeElementos: 0,
  totalDePaginas: 0,
  primeira: true,
  ultima: true,
};
