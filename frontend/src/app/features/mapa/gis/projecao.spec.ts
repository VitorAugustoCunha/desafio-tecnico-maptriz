import { describe, expect, it } from 'vitest';

import { Posicao, areaEmMetrosQuadrados, centroDoAnel, retanguloEmTornoDe } from './projecao';

/**
 * Testes da projeção usada na pré-visualização do lote.
 *
 * <p>O que se prova aqui é que o cliente constrói o retângulo com a mesma regra
 * do servidor: projetar para EPSG:31982, somar metade da largura/comprimento em
 * cada eixo, voltar para 4326. Se isso divergir, o usuário vê na tela uma forma
 * diferente da que fica gravada.
 *
 * <p>Curitiba, dentro da zona UTM 22S coberta pelo EPSG:31982.
 */
describe('projeção do lote', () => {
  const LON = -49.292;
  const LAT = -25.442;

  describe('retanguloEmTornoDe', () => {
    it('devolve um anel fechado com 5 posições', () => {
      const anel = retanguloEmTornoDe(LON, LAT, 20, 50);

      expect(anel).not.toBeNull();
      expect(anel).toHaveLength(5);
      expect(anel?.[0]).toEqual(anel?.[4]);
    });

    it('produz um retângulo com a área das dimensões informadas', () => {
      const anel = retanguloEmTornoDe(LON, LAT, 20, 50);

      // 20 m x 50 m = 1000 m², medidos no plano projetado.
      expect(areaEmMetrosQuadrados(anel!)).toBeCloseTo(1000, 0);
    });

    it('o ponto informado é o CENTRO do retângulo, não um canto', () => {
      const anel = retanguloEmTornoDe(LON, LAT, 20, 50);
      const centro = centroDoAnel(anel!);

      expect(centro?.[0]).toBeCloseTo(LON, 6);
      expect(centro?.[1]).toBeCloseTo(LAT, 6);
    });

    it('respeita a orientação dos eixos: largura em X, comprimento em Y', () => {
      const anel = retanguloEmTornoDe(LON, LAT, 20, 50)!;

      const longitudes = anel.map((p) => p[0]);
      const latitudes = anel.map((p) => p[1]);

      const larguraEmGraus = Math.max(...longitudes) - Math.min(...longitudes);
      const alturaEmGraus = Math.max(...latitudes) - Math.min(...latitudes);

      // 50 m no eixo norte-sul contra 20 m no leste-oeste: a altura tem que ser
      // visivelmente maior que a largura.
      expect(alturaEmGraus).toBeGreaterThan(larguraEmGraus);
    });

    it('dimensões diferentes geram áreas proporcionais', () => {
      const pequeno = retanguloEmTornoDe(LON, LAT, 10, 10)!;
      const grande = retanguloEmTornoDe(LON, LAT, 20, 20)!;

      expect(areaEmMetrosQuadrados(pequeno)).toBeCloseTo(100, 0);
      // Dobrar os dois lados quadruplica a área.
      expect(areaEmMetrosQuadrados(grande)).toBeCloseTo(400, 0);
    });

    it('devolve null quando não há retângulo a montar', () => {
      expect(retanguloEmTornoDe(LON, LAT, 0, 50)).toBeNull();
      expect(retanguloEmTornoDe(LON, LAT, -5, 50)).toBeNull();
      expect(retanguloEmTornoDe(LON, LAT, 20, 0)).toBeNull();
      expect(retanguloEmTornoDe(Number.NaN, LAT, 20, 50)).toBeNull();
    });
  });

  describe('areaEmMetrosQuadrados', () => {
    it('mede em metros, não em graus', () => {
      const anel = retanguloEmTornoDe(LON, LAT, 100, 100)!;

      // Em graus o número seria minúsculo; em metros são 10.000 m².
      expect(areaEmMetrosQuadrados(anel)).toBeCloseTo(10000, -1);
    });

    it('independe do sentido do traçado (horário ou anti-horário)', () => {
      const anel = retanguloEmTornoDe(LON, LAT, 20, 50)!;
      const invertido = [...anel].reverse() as Posicao[];

      expect(areaEmMetrosQuadrados(invertido)).toBeCloseTo(areaEmMetrosQuadrados(anel), 2);
    });

    it('devolve zero para anel degenerado', () => {
      expect(areaEmMetrosQuadrados([])).toBe(0);
      expect(
        areaEmMetrosQuadrados([
          [LON, LAT],
          [LON, LAT],
        ]),
      ).toBe(0);
    });
  });

  describe('centroDoAnel', () => {
    it('ignora a posição repetida do fechamento', () => {
      const quadrado: Posicao[] = [
        [-49.293, -25.443],
        [-49.291, -25.443],
        [-49.291, -25.441],
        [-49.293, -25.441],
        [-49.293, -25.443],
      ];

      const centro = centroDoAnel(quadrado);

      expect(centro?.[0]).toBeCloseTo(-49.292, 6);
      expect(centro?.[1]).toBeCloseTo(-25.442, 6);
    });

    it('devolve null para anel degenerado', () => {
      expect(centroDoAnel([])).toBeNull();
    });
  });
});
