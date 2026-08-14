import { Injectable, OnDestroy } from '@angular/core';

import { prepararColecao } from './gis-pipeline';
import { ColecaoDeFeicoes, RespostaDoWorker, ResultadoPreparado } from './gis-protocolo';

/**
 * Cliente do Web Worker GIS.
 *
 * <p>Responsabilidades:
 *
 * <ul>
 *   <li>criar o worker sob demanda e derrubar tudo no destroy;</li>
 *   <li>numerar os pedidos e <b>descartar resposta obsoleta</b> — arrastar o
 *       mapa gera pedidos em sequencia, e sem isso um resultado antigo que
 *       chegue depois sobrescreveria o atual;</li>
 *   <li>cair para execucao sincrona quando nao existe {@code Worker} (SSR,
 *       ambiente de teste, navegador sem suporte), com o mesmo resultado.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class GisWorkerClient implements OnDestroy {
  private worker: Worker | null = null;
  private proximoRequestId = 1;

  /**
   * Marcado quando o worker falha.
   *
   * <p>Uma vez que o worker quebrou, nao adianta recria-lo: o motivo mais
   * provavel e o proprio codigo do worker, e tentar de novo a cada movimento do
   * mapa daria um ciclo de falhas. A partir dai a sessao segue no fallback
   * sincrono, que e mais lento mas entrega o mesmo resultado.
   */
  private workerIndisponivel = false;

  /** Somente a resposta deste id interessa; qualquer outra e descartada. */
  private requestIdAtual = 0;

  private readonly pendentes = new Map<
    number,
    { resolver: (resultado: ResultadoPreparado) => void; rejeitar: (erro: Error) => void }
  >();

  /** `true` quando o processamento esta rodando fora da thread principal. */
  get usandoWorker(): boolean {
    return this.worker !== null;
  }

  /**
   * Prepara a colecao, no worker quando possivel.
   *
   * <p>Cada chamada invalida a anterior: a promessa de um pedido superado nunca
   * resolve com dado obsoleto — ela e rejeitada com {@link PedidoObsoletoError},
   * que o chamador ignora.
   */
  preparar(colecao: ColecaoDeFeicoes): Promise<ResultadoPreparado> {
    const requestId = this.proximoRequestId++;

    this.cancelarPendentes(requestId);
    this.requestIdAtual = requestId;

    const worker = this.obterWorker();

    if (worker === null) {
      // Fallback sincrono: mesmo codigo, mesma saida, sem worker.
      try {
        return Promise.resolve(prepararColecao(colecao));
      } catch (erro) {
        return Promise.reject(erro instanceof Error ? erro : new Error(String(erro)));
      }
    }

    return new Promise<ResultadoPreparado>((resolver, rejeitar) => {
      this.pendentes.set(requestId, { resolver, rejeitar });
      worker.postMessage({ tipo: 'preparar', requestId, colecao });
    });
  }

  ngOnDestroy(): void {
    this.cancelarPendentes(Number.MAX_SAFE_INTEGER);
    this.worker?.terminate();
    this.worker = null;
  }

  private obterWorker(): Worker | null {
    if (this.worker !== null) {
      return this.worker;
    }

    // `typeof Worker === 'undefined'` cobre SSR e o ambiente de teste (jsdom).
    if (this.workerIndisponivel || typeof Worker === 'undefined') {
      return null;
    }

    try {
      this.worker = new Worker(new URL('./gis.worker', import.meta.url), { type: 'module' });
      this.worker.addEventListener('message', (evento: MessageEvent<RespostaDoWorker>) =>
        this.aoReceber(evento.data),
      );
      this.worker.addEventListener('error', () => this.aoFalharWorker());
      return this.worker;
    } catch {
      // Sem worker disponivel, segue no fallback sincrono em vez de quebrar a tela.
      this.worker = null;
      return null;
    }
  }

  private aoReceber(resposta: RespostaDoWorker): void {
    const pendente = this.pendentes.get(resposta.requestId);
    if (pendente === undefined) {
      return;
    }

    this.pendentes.delete(resposta.requestId);

    // Chegou fora de ordem: um pedido mais novo ja assumiu. Descarta.
    if (resposta.requestId !== this.requestIdAtual) {
      pendente.rejeitar(new PedidoObsoletoError(resposta.requestId));
      return;
    }

    if (resposta.tipo === 'pronto') {
      pendente.resolver(resposta.resultado);
      return;
    }

    if (resposta.tipo === 'cancelado') {
      pendente.rejeitar(new PedidoObsoletoError(resposta.requestId));
      return;
    }

    pendente.rejeitar(new Error(resposta.mensagem));
  }

  private aoFalharWorker(): void {
    const erro = new Error('O processamento das feições do mapa falhou.');

    for (const pendente of this.pendentes.values()) {
      pendente.rejeitar(erro);
    }
    this.pendentes.clear();

    // Worker quebrado nao volta sozinho: derruba, marca como indisponivel e o
    // proximo pedido cai no fallback sincrono, que e lento mas funciona.
    this.workerIndisponivel = true;
    this.worker?.terminate();
    this.worker = null;
  }

  private cancelarPendentes(novoRequestId: number): void {
    for (const [id, pendente] of this.pendentes) {
      if (id >= novoRequestId) {
        continue;
      }
      this.worker?.postMessage({ tipo: 'cancelar', requestId: id });
      pendente.rejeitar(new PedidoObsoletoError(id));
      this.pendentes.delete(id);
    }
  }
}

/** Pedido superado por outro mais recente. Nao e falha: e resultado que nao interessa mais. */
export class PedidoObsoletoError extends Error {
  constructor(readonly requestId: number) {
    super(`Pedido ${requestId} foi superado por outro mais recente.`);
    this.name = 'PedidoObsoletoError';
  }
}

export function ehPedidoObsoleto(erro: unknown): boolean {
  return erro instanceof PedidoObsoletoError;
}
