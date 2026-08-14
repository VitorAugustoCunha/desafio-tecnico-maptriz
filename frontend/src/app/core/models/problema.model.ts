/**
 * Resposta de erro da API, em Problem Details (RFC 9457).
 *
 * <p>Tipar isso e o que permite a tela reagir ao erro em vez de so mostrar
 * "algo deu errado": com `erros` por campo, o formulario marca o input certo;
 * com `idImovelConflitante`, o mapa consegue destacar o lote que esta no caminho.
 */
export interface ProblemDetail {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail: string;
  readonly erros?: readonly ErroDeCampo[];
  readonly idImovelConflitante?: number;
  readonly segundosParaNovaTentativa?: number;
}

export interface ErroDeCampo {
  readonly campo: string;
  readonly mensagem: string;
}

export const TIPO_PROBLEMA = {
  validacao: 'urn:webgis:problema:validacao',
  naoEncontrado: 'urn:webgis:problema:nao-encontrado',
  conflitoDeDados: 'urn:webgis:problema:conflito-de-dados',
  conflitoEspacial: 'urn:webgis:problema:conflito-espacial',
  corpoInvalido: 'urn:webgis:problema:corpo-invalido',
  capacidadeExcedida: 'urn:webgis:problema:capacidade-excedida',
} as const;

/** Erro ja traduzido para o que a interface precisa mostrar. */
export interface ErroDaApi {
  readonly mensagem: string;
  readonly status: number;
  readonly tipo: string;
  readonly errosPorCampo: ReadonlyMap<string, string>;
  readonly idImovelConflitante: number | null;
}
