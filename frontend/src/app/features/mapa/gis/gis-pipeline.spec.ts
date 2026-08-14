import { describe, expect, it } from 'vitest';

import { coordenadaValida, prepararColecao } from './gis-pipeline';
import { ColecaoDeFeicoes, FeicaoGeoJson } from './gis-protocolo';

/**
 * Testes das funcoes puras do pipeline GIS.
 *
 * <p>Rodam sem Worker e sem TestBed: a logica foi extraida justamente para isso.
 * Testar atraves do `postMessage` seria lento, sujeito a corrida e nao provaria
 * mais nada.
 */
describe('pipeline GIS', () => {
  const ponto = (lon: number, lat: number, extras: Record<string, unknown> = {}): FeicaoGeoJson =>
    ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [lon, lat] },
      properties: { id: 1, municipio: 'Curitiba', areaM2: 100, ...extras },
    }) as FeicaoGeoJson;

  const colecao = (features: FeicaoGeoJson[]): ColecaoDeFeicoes => ({
    type: 'FeatureCollection',
    features,
  });

  describe('coordenadaValida', () => {
    it('aceita coordenada dentro da faixa', () => {
      expect(coordenadaValida(-49.29, -25.44)).toBe(true);
      expect(coordenadaValida(0, 0)).toBe(true);
      expect(coordenadaValida(-180, -90)).toBe(true);
      expect(coordenadaValida(180, 90)).toBe(true);
    });

    it('recusa coordenada fora da faixa', () => {
      expect(coordenadaValida(-181, 0)).toBe(false);
      expect(coordenadaValida(0, 91)).toBe(false);
    });

    it('recusa nao-numero, NaN e infinito', () => {
      expect(coordenadaValida(null, 0)).toBe(false);
      expect(coordenadaValida(undefined, 0)).toBe(false);
      expect(coordenadaValida('-49.29', -25.44)).toBe(false);
      expect(coordenadaValida(Number.NaN, 0)).toBe(false);
      expect(coordenadaValida(Number.POSITIVE_INFINITY, 0)).toBe(false);
    });
  });

  describe('prepararColecao', () => {
    it('achata as coordenadas em um Float64Array transferivel', () => {
      const resultado = prepararColecao(colecao([ponto(-49.29, -25.44), ponto(-46.69, -23.56)]));

      expect(resultado.coordenadas).toBeInstanceOf(Float64Array);
      expect(Array.from(resultado.coordenadas)).toEqual([-49.29, -25.44, -46.69, -23.56]);
    });

    it('descarta feicao com coordenada invalida sem derrubar o lote', () => {
      const invalida = {
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [null, -25.44] },
        properties: {},
      } as unknown as FeicaoGeoJson;

      const resultado = prepararColecao(colecao([ponto(-49.29, -25.44), invalida, ponto(-46.69, -23.56)]));

      expect(resultado.pontos).toHaveLength(2);
      expect(resultado.descartadas).toBe(1);
    });

    it('calcula limites que envolvem todas as feicoes', () => {
      const resultado = prepararColecao(colecao([ponto(-49.29, -25.44), ponto(-46.69, -23.56)]));

      expect(resultado.estatisticas.limites).toEqual({
        minLon: -49.29,
        minLat: -25.44,
        maxLon: -46.69,
        maxLat: -23.56,
      });
    });

    it('soma area e calcula a media apenas das feicoes individuais', () => {
      const resultado = prepararColecao(
        colecao([ponto(-49.29, -25.44, { areaM2: 100 }), ponto(-49.3, -25.45, { areaM2: 300 })]),
      );

      expect(resultado.estatisticas.areaTotalM2).toBe(400);
      expect(resultado.estatisticas.areaMediaM2).toBe(200);
    });

    it('conta o cluster pela quantidade, sem deixar a area distorcer a media', () => {
      const cluster = ponto(-49.29, -25.44, { quantidade: 40, areaM2: undefined, id: undefined });
      const individual = ponto(-46.69, -23.56, { areaM2: 250 });

      const resultado = prepararColecao(colecao([cluster, individual]));

      expect(resultado.estatisticas.totalDeImoveis).toBe(41);
      expect(resultado.estatisticas.areaTotalM2).toBe(250);
      expect(resultado.estatisticas.areaMediaM2).toBe(250);
    });

    it('agrupa por municipio em ordem decrescente de quantidade', () => {
      const resultado = prepararColecao(
        colecao([
          ponto(-49.29, -25.44, { municipio: 'Curitiba' }),
          ponto(-49.3, -25.45, { municipio: 'Curitiba' }),
          ponto(-46.69, -23.56, { municipio: 'São Paulo' }),
        ]),
      );

      expect(resultado.estatisticas.porMunicipio).toEqual([
        { municipio: 'Curitiba', quantidade: 2 },
        { municipio: 'São Paulo', quantidade: 1 },
      ]);
    });

    it('converte poligono preservando o anel externo', () => {
      const poligono = {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [
            [
              [-49.2921, -25.4421],
              [-49.2919, -25.4421],
              [-49.2919, -25.4419],
              [-49.2921, -25.4419],
              [-49.2921, -25.4421],
            ],
          ],
        },
        properties: { id: 7, poligono: true },
      } as unknown as FeicaoGeoJson;

      const resultado = prepararColecao(colecao([poligono]));

      expect(resultado.poligonos).toHaveLength(1);
      expect(resultado.poligonos[0]?.anel).toHaveLength(5);
      expect(resultado.poligonos[0]?.id).toBe(7);
    });

    it('descarta poligono cujo anel nao fecha', () => {
      const aberto = {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [
            [
              [-49.29, -25.44],
              [-49.28, -25.44],
            ],
          ],
        },
        properties: {},
      } as unknown as FeicaoGeoJson;

      const resultado = prepararColecao(colecao([aberto]));

      expect(resultado.poligonos).toHaveLength(0);
      expect(resultado.descartadas).toBe(1);
    });

    it('rotula cluster pela quantidade e feicao individual pelo titular', () => {
      const resultado = prepararColecao(
        colecao([
          ponto(-49.29, -25.44, { quantidade: 12 }),
          ponto(-46.69, -23.56, { proprietarioNome: 'Maria Souza' }),
        ]),
      );

      expect(resultado.pontos[0]?.rotulo).toBe('12 imóveis');
      expect(resultado.pontos[1]?.rotulo).toBe('Maria Souza');
    });

    it('lida com colecao vazia sem quebrar', () => {
      const resultado = prepararColecao(colecao([]));

      expect(resultado.pontos).toHaveLength(0);
      expect(resultado.estatisticas.totalDeFeicoes).toBe(0);
      expect(resultado.estatisticas.limites).toBeNull();
      expect(resultado.coordenadas).toHaveLength(0);
    });
  });
});
