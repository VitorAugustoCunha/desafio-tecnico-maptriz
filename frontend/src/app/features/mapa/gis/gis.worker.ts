/// <reference lib="webworker" />

import { prepararColecao } from './gis-pipeline';
import { PedidoAoWorker, RespostaDoWorker } from './gis-protocolo';

/**
 * Web Worker do pipeline GIS.
 *
 * <p>Deliberadamente fino: recebe a mensagem, chama a funcao pura de
 * {@link prepararColecao} e devolve. Toda a logica fica fora daqui para ser
 * testavel sem subir Worker.
 *
 * <p>Renderizacao e interacao com o OpenLayers continuam na thread principal —
 * o worker nao tem DOM, e mover o mapa para ca nao e possivel nem desejavel.
 */

/**
 * Pedidos cancelados.
 *
 * <p>Cancelamento aqui e logico: nao da para interromper um laco JavaScript no
 * meio. O que se ganha e nao pagar o custo de serializar e devolver um
 * resultado que ninguem mais vai usar — quando o usuario arrasta o mapa, o
 * pedido anterior ja nao interessa.
 */
const cancelados = new Set<number>();

addEventListener('message', (evento: MessageEvent<PedidoAoWorker>) => {
  const pedido = evento.data;

  if (pedido.tipo === 'cancelar') {
    cancelados.add(pedido.requestId);
    return;
  }

  if (pedido.tipo !== 'preparar') {
    return;
  }

  const { requestId, colecao } = pedido;

  try {
    const resultado = prepararColecao(colecao);

    if (cancelados.delete(requestId)) {
      responder({ tipo: 'cancelado', requestId });
      return;
    }

    // O buffer de coordenadas e transferido, nao copiado: em lotes grandes a
    // copia estrutural do postMessage custa mais que o proprio processamento.
    // Depois da transferencia o buffer fica inutilizavel aqui dentro — o que
    // nao e problema, porque o worker nao guarda estado entre mensagens.
    postMessage({ tipo: 'pronto', requestId, resultado } satisfies RespostaDoWorker, [
      resultado.coordenadas.buffer,
    ]);
  } catch (erro) {
    // `Error` nao sobrevive ao postMessage com a stack; envia-se so a mensagem.
    responder({
      tipo: 'erro',
      requestId,
      mensagem: erro instanceof Error ? erro.message : 'Falha ao preparar as feições do mapa.',
    });
  }
});

function responder(resposta: RespostaDoWorker): void {
  postMessage(resposta);
}
