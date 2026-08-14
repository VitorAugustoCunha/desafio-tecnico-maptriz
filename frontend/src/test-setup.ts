/**
 * Preparação do ambiente de teste.
 *
 * <p>O jsdom não implementa algumas APIs de layout que o OpenLayers usa para
 * saber o tamanho do canvas. Sem elas, qualquer componente que monte um mapa
 * quebra com `ResizeObserver is not defined` — inclusive o formulário de
 * imóvel, que embute o editor de lote.
 *
 * <p>São dublês mínimos e inertes de propósito: em teste ninguém observa
 * redimensionamento de verdade, e um polyfill que tentasse simular layout daria
 * uma falsa sensação de cobertura. O que os testes verificam do mapa são as
 * funções puras de projeção e o protocolo do worker, ambos independentes de DOM.
 */

class ResizeObserverInerte implements ResizeObserver {
  observe(): void {
    // sem layout no jsdom, não há o que observar
  }

  unobserve(): void {
    // idem
  }

  disconnect(): void {
    // idem
  }
}

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = ResizeObserverInerte as unknown as typeof ResizeObserver;
}

// O OpenLayers consulta o contexto 2D para medir texto dos rótulos.
if (typeof HTMLCanvasElement !== 'undefined' && HTMLCanvasElement.prototype.getContext !== undefined) {
  const original = HTMLCanvasElement.prototype.getContext;

  HTMLCanvasElement.prototype.getContext = function (
    this: HTMLCanvasElement,
    ...args: Parameters<typeof original>
  ): ReturnType<typeof original> {
    try {
      return original.apply(this, args);
    } catch {
      // jsdom sem canvas nativo: devolve null em vez de derrubar o teste.
      return null;
    }
  } as typeof original;
}
