/**
 * Tipos do dominio de imovel.
 *
 * <p>O codigo original usava `any` em tudo — inclusive no modelo —, entao um erro
 * de digitacao como `i.propietario` compilava e so aparecia como celula vazia na
 * tela. Aqui o compilador cobra.
 */

/** Linha da listagem. Menor que {@link Imovel}: sem datas nem dimensoes. */
export interface ImovelListItem {
  readonly id: number;
  readonly proprietarioId: number;
  readonly proprietarioNome: string;
  readonly municipio: string;
  readonly uf: string;
  readonly bairro: string;
  readonly rua: string;
  readonly numero: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly areaM2: number;
  readonly ativo: boolean;
}

/** Imovel completo, como vem do detalhe, da criacao e da atualizacao. */
export interface Imovel {
  readonly id: number;
  readonly proprietario: ProprietarioResumo;
  readonly municipio: string;
  readonly uf: string;
  readonly bairro: string;
  readonly rua: string;
  readonly numero: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly areaM2: number;
  readonly larguraM: number | null;
  readonly comprimentoM: number | null;
  /** `true` quando existe poligono em EPSG:31982 gravado. */
  readonly possuiGeometria: boolean;
  readonly ativo: boolean;
  readonly criadoEm: string;
  readonly atualizadoEm: string;
}

export interface ProprietarioResumo {
  readonly id: number;
  readonly nome: string;
}

/** Corpo enviado na criacao e na atualizacao. */
export interface ImovelRequest {
  readonly proprietarioNome: string;
  readonly municipio: string;
  readonly uf: string;
  readonly bairro: string;
  readonly rua: string;
  readonly numero: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly areaM2: number | null;
  readonly larguraM: number | null;
  readonly comprimentoM: number | null;
  readonly ativo: boolean;
}

/** Campos pelos quais a API aceita ordenar (whitelist espelhada do backend). */
export type OrdemImovel = 'id' | 'municipio' | 'area' | 'criado_em' | 'proprietario';

export type Direcao = 'asc' | 'desc';

/** Parametros completos de uma consulta de listagem. */
export interface ConsultaImoveis {
  readonly proprietarioId: number | null;
  readonly proprietarioNome: string;
  readonly municipio: string;
  readonly pagina: number;
  readonly tamanho: number;
  readonly ordenarPor: OrdemImovel;
  readonly direcao: Direcao;
}

export const CONSULTA_PADRAO: ConsultaImoveis = {
  proprietarioId: null,
  proprietarioNome: '',
  municipio: '',
  pagina: 0,
  tamanho: 20,
  ordenarPor: 'id',
  direcao: 'asc',
};

/**
 * Chave de identidade de uma consulta.
 *
 * <p>E o que o cache usa para decidir se ja tem a resposta em maos. Duas
 * consultas com os mesmos parametros produzem a mesma chave.
 */
export function chaveDaConsulta(consulta: ConsultaImoveis): string {
  return [
    consulta.proprietarioId ?? '',
    consulta.proprietarioNome.trim().toLowerCase(),
    consulta.municipio.trim().toLowerCase(),
    consulta.pagina,
    consulta.tamanho,
    consulta.ordenarPor,
    consulta.direcao,
  ].join('|');
}
