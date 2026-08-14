import proj4 from 'proj4';
import { register } from 'ol/proj/proj4';
import { transform } from 'ol/proj';

/**
 * Registro da projeção EPSG:31982 no OpenLayers.
 *
 * <p>O OpenLayers só conhece 4326 e 3857 de fábrica. Sem registrar a 31982, o
 * frontend não teria como pré-visualizar o retângulo do lote <b>exatamente</b>
 * como o backend o constrói — sobraria aproximar em Web Mercator, e a forma
 * desenhada na tela discordaria da que fica gravada no banco.
 *
 * <p>É a mesma definição que o PROJ usa dentro do PostGIS: SIRGAS 2000 / UTM
 * zona 22S. Ver docs/DECISIONS.md, ADR-004, para a limitação dessa zona.
 */
export const SIRGAS_2000_UTM_22S = 'EPSG:31982';
export const WGS84 = 'EPSG:4326';

let registrada = false;

/** Idempotente: pode ser chamada por qualquer componente que precise da projeção. */
export function registrarProjecoes(): void {
  if (registrada) {
    return;
  }

  proj4.defs(
    SIRGAS_2000_UTM_22S,
    '+proj=utm +zone=22 +south +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs',
  );
  register(proj4);

  registrada = true;
}

/** Posição `[longitude, latitude]`, como manda o GeoJSON. */
export type Posicao = readonly [number, number];

/**
 * Retângulo do lote a partir do centro e das dimensões em metros.
 *
 * <p>Reproduz, no cliente, exatamente o que a função SQL {@code webgis_retangulo}
 * faz no servidor: projeta o centro para 31982, soma metade da largura em X e
 * metade do comprimento em Y para cada lado, e volta para 4326.
 *
 * <p>Por isso a pré-visualização não é uma ilustração aproximada — é a mesma
 * geometria que será gravada.
 *
 * @returns anel fechado (a última posição repete a primeira), ou `null` quando
 *          os dados não formam um retângulo
 */
export function retanguloEmTornoDe(
  longitude: number,
  latitude: number,
  larguraM: number,
  comprimentoM: number,
): Posicao[] | null {
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    return null;
  }
  if (!(larguraM > 0) || !(comprimentoM > 0)) {
    return null;
  }

  registrarProjecoes();

  const [x, y] = transform([longitude, latitude], WGS84, SIRGAS_2000_UTM_22S);

  const meiaLargura = larguraM / 2;
  const meioComprimento = comprimentoM / 2;

  const cantos: Posicao[] = [
    [x - meiaLargura, y - meioComprimento],
    [x + meiaLargura, y - meioComprimento],
    [x + meiaLargura, y + meioComprimento],
    [x - meiaLargura, y + meioComprimento],
  ];

  const anel = cantos.map((canto) => {
    const [lon, lat] = transform([canto[0], canto[1]], SIRGAS_2000_UTM_22S, WGS84);
    return [lon, lat] as Posicao;
  });

  // Fecha o anel, como o GeoJSON exige.
  anel.push(anel[0] as Posicao);

  return anel;
}

/**
 * Área do polígono em m², medida no plano projetado.
 *
 * <p>Em 31982 a unidade é o metro, então a fórmula do laço (shoelace) devolve
 * metros quadrados direto. Calcular sobre graus daria um número sem significado
 * físico.
 */
export function areaEmMetrosQuadrados(anel: readonly Posicao[]): number {
  if (anel.length < 4) {
    return 0;
  }

  registrarProjecoes();

  const projetado = anel.map((posicao) => transform([posicao[0], posicao[1]], WGS84, SIRGAS_2000_UTM_22S));

  let soma = 0;
  for (let i = 0; i < projetado.length - 1; i++) {
    const atual = projetado[i];
    const proximo = projetado[i + 1];
    if (atual === undefined || proximo === undefined) {
      continue;
    }
    soma += atual[0] * proximo[1] - proximo[0] * atual[1];
  }

  return Math.abs(soma) / 2;
}

/** Centro geométrico do anel, em 4326. Usado como ponto do imóvel no modo desenho. */
export function centroDoAnel(anel: readonly Posicao[]): Posicao | null {
  if (anel.length < 4) {
    return null;
  }

  // Ignora a última posição, que repete a primeira.
  const posicoes = anel.slice(0, -1);

  const somaLon = posicoes.reduce((total, p) => total + p[0], 0);
  const somaLat = posicoes.reduce((total, p) => total + p[1], 0);

  return [somaLon / posicoes.length, somaLat / posicoes.length];
}
