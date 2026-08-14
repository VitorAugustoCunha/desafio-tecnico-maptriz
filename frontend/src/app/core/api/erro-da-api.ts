import { HttpErrorResponse } from '@angular/common/http';

import { ErroDaApi, ProblemDetail } from '../models/problema.model';

/**
 * Traduz a falha HTTP no que a interface precisa mostrar.
 *
 * <p>No codigo original nenhuma chamada tinha callback de erro: uma falha de
 * rede nao produzia sinal nenhum na tela, e uma falha do servidor virava
 * "Imovel cadastrado!". Aqui todo erro vira uma mensagem util.
 */
export function traduzirErro(erro: unknown): ErroDaApi {
  if (!(erro instanceof HttpErrorResponse)) {
    return {
      mensagem: 'Ocorreu um erro inesperado.',
      status: 0,
      tipo: 'desconhecido',
      errosPorCampo: new Map(),
      idImovelConflitante: null,
    };
  }

  // status 0 = requisicao nem chegou ao servidor (offline, DNS, CORS).
  if (erro.status === 0) {
    return {
      mensagem: 'Não foi possível falar com o servidor. Verifique sua conexão.',
      status: 0,
      tipo: 'rede',
      errosPorCampo: new Map(),
      idImovelConflitante: null,
    };
  }

  const problema = erro.error as ProblemDetail | null;

  const errosPorCampo = new Map<string, string>();
  for (const item of problema?.erros ?? []) {
    // O primeiro erro de cada campo basta: mostrar tres mensagens no mesmo
    // input so atrapalha quem esta preenchendo.
    if (!errosPorCampo.has(item.campo)) {
      errosPorCampo.set(item.campo, item.mensagem);
    }
  }

  return {
    mensagem: problema?.detail ?? mensagemPadrao(erro.status),
    status: erro.status,
    tipo: problema?.type ?? 'desconhecido',
    errosPorCampo,
    idImovelConflitante: problema?.idImovelConflitante ?? null,
  };
}

function mensagemPadrao(status: number): string {
  switch (status) {
    case 400:
      return 'Os dados enviados são inválidos.';
    case 404:
      return 'Registro não encontrado.';
    case 409:
      return 'A operação conflita com um registro existente.';
    case 503:
      return 'O serviço está temporariamente indisponível. Tente novamente em instantes.';
    default:
      return 'Ocorreu um erro ao processar a solicitação.';
  }
}
