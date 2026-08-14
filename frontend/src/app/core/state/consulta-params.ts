import { Params } from '@angular/router';

import { CONSULTA_PADRAO, ConsultaImoveis, Direcao, OrdemImovel } from '../models/imovel.model';

const ORDENS: readonly OrdemImovel[] = ['id', 'municipio', 'area', 'criado_em', 'proprietario'];
const DIRECOES: readonly Direcao[] = ['asc', 'desc'];

const TAMANHO_MINIMO = 5;
const TAMANHO_MAXIMO = 100;

/**
 * Conversao entre a consulta da listagem e a query string da rota.
 *
 * <p>Manter os parametros na URL e o que faz o deep-link e o F5 funcionarem: a
 * tela e reconstruida a partir do endereco, nao de estado escondido. E tambem o
 * que permite voltar da edicao para <b>exatamente</b> a mesma consulta — se a
 * volta perdesse os filtros, a chave de cache mudaria e uma nova requisicao
 * seria disparada, quebrando o requisito 3.
 */
export function consultaParaQueryParams(consulta: ConsultaImoveis): Params {
  const params: Params = {};

  if (consulta.proprietarioId !== null) {
    params['proprietarioId'] = consulta.proprietarioId;
  }
  if (consulta.proprietarioNome.trim()) {
    params['proprietarioNome'] = consulta.proprietarioNome.trim();
  }
  if (consulta.municipio.trim()) {
    params['municipio'] = consulta.municipio.trim();
  }
  if (consulta.pagina !== CONSULTA_PADRAO.pagina) {
    params['pagina'] = consulta.pagina;
  }
  if (consulta.tamanho !== CONSULTA_PADRAO.tamanho) {
    params['tamanho'] = consulta.tamanho;
  }
  if (consulta.ordenarPor !== CONSULTA_PADRAO.ordenarPor) {
    params['ordenarPor'] = consulta.ordenarPor;
  }
  if (consulta.direcao !== CONSULTA_PADRAO.direcao) {
    params['direcao'] = consulta.direcao;
  }

  return params;
}

/** Le a consulta da URL, ignorando valor invalido em vez de quebrar a tela. */
export function queryParamsParaConsulta(params: Params): ConsultaImoveis {
  return {
    proprietarioId: numeroOuNulo(params['proprietarioId']),
    proprietarioNome: texto(params['proprietarioNome']),
    municipio: texto(params['municipio']),
    pagina: Math.max(0, inteiro(params['pagina'], CONSULTA_PADRAO.pagina)),
    tamanho: limitar(inteiro(params['tamanho'], CONSULTA_PADRAO.tamanho), TAMANHO_MINIMO, TAMANHO_MAXIMO),
    ordenarPor: umDe(params['ordenarPor'], ORDENS, CONSULTA_PADRAO.ordenarPor),
    direcao: umDe(params['direcao'], DIRECOES, CONSULTA_PADRAO.direcao),
  };
}

function texto(valor: unknown): string {
  return typeof valor === 'string' ? valor : '';
}

function inteiro(valor: unknown, padrao: number): number {
  const numero = Number(valor);
  return Number.isInteger(numero) ? numero : padrao;
}

function numeroOuNulo(valor: unknown): number | null {
  const numero = Number(valor);
  return valor !== undefined && valor !== null && Number.isFinite(numero) ? numero : null;
}

function limitar(valor: number, minimo: number, maximo: number): number {
  return Math.min(Math.max(valor, minimo), maximo);
}

function umDe<T extends string>(valor: unknown, aceitos: readonly T[], padrao: T): T {
  return typeof valor === 'string' && (aceitos as readonly string[]).includes(valor) ? (valor as T) : padrao;
}
