import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ColecaoDeFeicoes, RespostaDoWorker } from './gis-protocolo';
import { GisWorkerClient, ehPedidoObsoleto } from './gis-worker.client';

/**
 * Testes do protocolo do worker.
 *
 * <p>O `Worker` real e substituido por um dublê controlado pelo teste: assim da
 * para responder fora de ordem de proposito, que e justamente o cenario que o
 * `requestId` existe para resolver e que nao acontece de forma confiavel com um
 * worker de verdade.
 */
describe('GisWorkerClient', () => {
  const colecao: ColecaoDeFeicoes = {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [-49.29, -25.44] },
        properties: { id: 1, municipio: 'Curitiba', areaM2: 100 },
      },
    ],
  };

  describe('sem Worker disponivel (SSR / ambiente de teste)', () => {
    let workerOriginal: typeof globalThis.Worker | undefined;

    beforeEach(() => {
      workerOriginal = globalThis.Worker;
      // @ts-expect-error remocao proposital para simular ambiente sem Worker
      delete globalThis.Worker;

      TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    });

    afterEach(() => {
      if (workerOriginal !== undefined) {
        globalThis.Worker = workerOriginal;
      }
    });

    it('cai para execucao sincrona e devolve o mesmo resultado', async () => {
      const cliente = TestBed.inject(GisWorkerClient);

      const resultado = await cliente.preparar(colecao);

      expect(cliente.usandoWorker).toBe(false);
      expect(resultado.pontos).toHaveLength(1);
      expect(resultado.estatisticas.totalDeImoveis).toBe(1);
    });
  });

  describe('com Worker', () => {
    let ultimoWorker: WorkerFalso;
    let workerOriginal: typeof globalThis.Worker | undefined;

    /** Dublê de Worker: registra as mensagens enviadas e responde sob comando. */
    class WorkerFalso {
      readonly enviadas: unknown[] = [];
      private ouvintes: ((evento: MessageEvent<RespostaDoWorker>) => void)[] = [];
      private ouvintesDeErro: (() => void)[] = [];

      constructor() {
        ultimoWorker = this;
      }

      postMessage(mensagem: unknown): void {
        this.enviadas.push(mensagem);
      }

      addEventListener(tipo: string, ouvinte: (evento: MessageEvent<RespostaDoWorker>) => void): void {
        if (tipo === 'message') {
          this.ouvintes.push(ouvinte);
        } else if (tipo === 'error') {
          this.ouvintesDeErro.push(ouvinte as unknown as () => void);
        }
      }

      terminate(): void {
        this.ouvintes = [];
        this.ouvintesDeErro = [];
      }

      responder(resposta: RespostaDoWorker): void {
        for (const ouvinte of this.ouvintes) {
          ouvinte({ data: resposta } as MessageEvent<RespostaDoWorker>);
        }
      }

      falhar(): void {
        for (const ouvinte of this.ouvintesDeErro) {
          ouvinte();
        }
      }
    }

    beforeEach(() => {
      workerOriginal = globalThis.Worker;
      globalThis.Worker = WorkerFalso as unknown as typeof globalThis.Worker;

      TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    });

    afterEach(() => {
      if (workerOriginal !== undefined) {
        globalThis.Worker = workerOriginal;
      }
    });

    it('envia o pedido com requestId e resolve com o resultado', async () => {
      const cliente = TestBed.inject(GisWorkerClient);

      const promessa = cliente.preparar(colecao);

      expect(ultimoWorker.enviadas).toHaveLength(1);
      const enviado = ultimoWorker.enviadas[0] as { tipo: string; requestId: number };
      expect(enviado.tipo).toBe('preparar');
      expect(enviado.requestId).toBe(1);

      ultimoWorker.responder({
        tipo: 'pronto',
        requestId: 1,
        resultado: {
          coordenadas: Float64Array.from([-49.29, -25.44]),
          pontos: [],
          poligonos: [],
          estatisticas: {
            totalDeFeicoes: 0,
            totalDeImoveis: 0,
            areaTotalM2: 0,
            areaMediaM2: 0,
            limites: null,
            porMunicipio: [],
          },
          descartadas: 0,
        },
      });

      await expect(promessa).resolves.toMatchObject({ descartadas: 0 });
      expect(cliente.usandoWorker).toBe(true);
    });

    it('DESCARTA resposta obsoleta: um pedido novo invalida o anterior', async () => {
      const cliente = TestBed.inject(GisWorkerClient);

      const primeira = cliente.preparar(colecao);
      const segunda = cliente.preparar(colecao);

      // A promessa do pedido superado e rejeitada como obsoleta.
      await expect(primeira).rejects.toSatisfy(ehPedidoObsoleto);

      // E o worker foi avisado para nao gastar tempo com ela.
      expect(ultimoWorker.enviadas).toContainEqual({ tipo: 'cancelar', requestId: 1 });

      ultimoWorker.responder({
        tipo: 'pronto',
        requestId: 2,
        resultado: {
          coordenadas: Float64Array.from([]),
          pontos: [],
          poligonos: [],
          estatisticas: {
            totalDeFeicoes: 0,
            totalDeImoveis: 7,
            areaTotalM2: 0,
            areaMediaM2: 0,
            limites: null,
            porMunicipio: [],
          },
          descartadas: 0,
        },
      });

      // Só o resultado do pedido mais recente chega ao chamador.
      await expect(segunda).resolves.toMatchObject({
        estatisticas: expect.objectContaining({ totalDeImoveis: 7 }),
      });
    });

    it('ignora resposta de requestId desconhecido sem quebrar', async () => {
      const cliente = TestBed.inject(GisWorkerClient);
      const promessa = cliente.preparar(colecao);

      // Resposta de um pedido que nao existe: nao pode resolver nem estourar.
      ultimoWorker.responder({ tipo: 'erro', requestId: 999, mensagem: 'ruido' });

      let resolvida = false;
      void promessa.then(() => (resolvida = true)).catch(() => undefined);
      await Promise.resolve();

      expect(resolvida).toBe(false);

      ultimoWorker.responder({ tipo: 'erro', requestId: 1, mensagem: 'de verdade' });
      await expect(promessa).rejects.toThrow('de verdade');
    });

    it('propaga erro serializado do worker', async () => {
      const cliente = TestBed.inject(GisWorkerClient);
      const promessa = cliente.preparar(colecao);

      ultimoWorker.responder({ tipo: 'erro', requestId: 1, mensagem: 'Falha ao preparar' });

      await expect(promessa).rejects.toThrow('Falha ao preparar');
    });

    it('trata cancelamento confirmado como pedido obsoleto', async () => {
      const cliente = TestBed.inject(GisWorkerClient);
      const promessa = cliente.preparar(colecao);

      ultimoWorker.responder({ tipo: 'cancelado', requestId: 1 });

      await expect(promessa).rejects.toSatisfy(ehPedidoObsoleto);
    });

    it('worker que falha derruba o pendente e volta ao fallback sincrono', async () => {
      const cliente = TestBed.inject(GisWorkerClient);
      const promessa = cliente.preparar(colecao);

      ultimoWorker.falhar();

      await expect(promessa).rejects.toThrow();
      expect(cliente.usandoWorker).toBe(false);

      // O proximo pedido ainda funciona, agora na thread principal.
      const resultado = await cliente.preparar(colecao);
      expect(resultado.pontos).toHaveLength(1);
    });

    it('termina o worker ao destruir o servico', () => {
      const cliente = TestBed.inject(GisWorkerClient);
      void cliente.preparar(colecao).catch(() => undefined);

      const encerrar = vi.spyOn(ultimoWorker, 'terminate');
      cliente.ngOnDestroy();

      expect(encerrar).toHaveBeenCalled();
      expect(cliente.usandoWorker).toBe(false);
    });
  });
});
