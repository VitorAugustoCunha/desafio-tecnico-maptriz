import {
  ColecaoDeFeicoes,
  ContagemPorMunicipio,
  EstatisticasDoLote,
  FeicaoGeoJson,
  Limites,
  PoligonoPreparado,
  PontoPreparado,
  ResultadoPreparado,
} from './gis-protocolo';

/**
 * Pipeline GIS: normaliza o GeoJSON recebido e calcula o que a tela precisa.
 *
 * <p><b>Funcoes puras, fora do worker.</b> O worker e so o lugar onde isto roda;
 * a logica mora aqui para poder ser testada direto, sem subir Worker no teste e
 * sem depender de `postMessage`. E o mesmo motivo pelo qual existe fallback
 * sincrono: em SSR ou em ambiente de teste sem `Worker`, chama-se esta funcao.
 *
 * <p><b>Por que isto justifica um worker.</b> E trabalho puramente de CPU sobre
 * lotes grandes: validar e achatar milhares de coordenadas, somar areas, agrupar
 * por municipio e montar o buffer de coordenadas. Rodando na thread principal
 * junto com a renderizacao do OpenLayers, um lote grande trava a interface. O
 * que o worker <b>nao</b> faz: clustering (isso e resolvido no PostGIS, onde nao
 * exige baixar tudo antes) nem chamada HTTP.
 */
export function prepararColecao(colecao: ColecaoDeFeicoes): ResultadoPreparado {
  const pontos: PontoPreparado[] = [];
  const poligonos: PoligonoPreparado[] = [];
  const coordenadas: number[] = [];

  let descartadas = 0;
  let totalDeImoveis = 0;
  let areaTotal = 0;
  // Feicao agregada (cluster) nao carrega area; a media so pode considerar as
  // feicoes individuais, senao o denominador infla e a media vira ficcao.
  let imoveisComArea = 0;
  let limites: Limites | null = null;

  const porMunicipio = new Map<string, number>();

  for (const feicao of colecao.features ?? []) {
    if (!feicaoValida(feicao)) {
      // Coordenada invalida nao pode derrubar o mapa inteiro: descarta a feicao,
      // conta quantas foram e segue.
      descartadas++;
      continue;
    }

    const propriedades = feicao.properties ?? {};
    const quantidade = propriedades.quantidade ?? 1;

    if (feicao.geometry.type === 'Point') {
      const [lon, lat] = feicao.geometry.coordinates;

      pontos.push({
        id: propriedades.id ?? null,
        lon,
        lat,
        quantidade,
        rotulo: rotuloDe(propriedades, quantidade),
        propriedades,
      });

      coordenadas.push(lon, lat);
      limites = expandir(limites, lon, lat);
    } else {
      const anel = feicao.geometry.coordinates[0];

      poligonos.push({
        id: propriedades.id ?? null,
        anel: anel.map(([lon, lat]) => [lon, lat] as const),
        propriedades,
      });

      for (const [lon, lat] of anel) {
        coordenadas.push(lon, lat);
        limites = expandir(limites, lon, lat);
      }
    }

    totalDeImoveis += quantidade;

    if (propriedades.areaM2 !== undefined && propriedades.quantidade === undefined) {
      areaTotal += propriedades.areaM2;
      imoveisComArea++;
    }

    const municipio = propriedades.municipio;
    if (municipio !== undefined && municipio !== '') {
      porMunicipio.set(municipio, (porMunicipio.get(municipio) ?? 0) + quantidade);
    }
  }

  const estatisticas: EstatisticasDoLote = {
    totalDeFeicoes: pontos.length + poligonos.length,
    totalDeImoveis,
    areaTotalM2: arredondar(areaTotal),
    areaMediaM2: imoveisComArea > 0 ? arredondar(areaTotal / imoveisComArea) : 0,
    limites,
    porMunicipio: ordenarPorQuantidade(porMunicipio),
  };

  return {
    coordenadas: Float64Array.from(coordenadas),
    pontos,
    poligonos,
    estatisticas,
    descartadas,
  };
}

/**
 * Coordenada precisa ser numero finito e estar dentro da faixa valida.
 *
 * <p>Sem esta checagem, um `null` de latitude vira `NaN` na projecao e o
 * OpenLayers desenha a camada inteira fora da tela — o sintoma aparece longe da
 * causa.
 */
export function coordenadaValida(lon: unknown, lat: unknown): boolean {
  return (
    typeof lon === 'number' &&
    typeof lat === 'number' &&
    Number.isFinite(lon) &&
    Number.isFinite(lat) &&
    lon >= -180 &&
    lon <= 180 &&
    lat >= -90 &&
    lat <= 90
  );
}

function feicaoValida(feicao: FeicaoGeoJson | null | undefined): feicao is FeicaoGeoJson {
  if (!feicao?.geometry) {
    return false;
  }

  if (feicao.geometry.type === 'Point') {
    const coordenadas = feicao.geometry.coordinates;
    return Array.isArray(coordenadas) && coordenadaValida(coordenadas[0], coordenadas[1]);
  }

  if (feicao.geometry.type === 'Polygon') {
    const anel = feicao.geometry.coordinates?.[0];
    // Um anel com menos de 4 posicoes nao fecha um poligono.
    if (!Array.isArray(anel) || anel.length < 4) {
      return false;
    }
    return anel.every((posicao) => coordenadaValida(posicao?.[0], posicao?.[1]));
  }

  return false;
}

function rotuloDe(propriedades: { proprietarioNome?: string; municipio?: string }, quantidade: number): string {
  if (quantidade > 1) {
    return `${quantidade} imóveis`;
  }
  return propriedades.proprietarioNome ?? propriedades.municipio ?? 'Imóvel';
}

function expandir(limites: Limites | null, lon: number, lat: number): Limites {
  if (limites === null) {
    return { minLon: lon, minLat: lat, maxLon: lon, maxLat: lat };
  }

  return {
    minLon: Math.min(limites.minLon, lon),
    minLat: Math.min(limites.minLat, lat),
    maxLon: Math.max(limites.maxLon, lon),
    maxLat: Math.max(limites.maxLat, lat),
  };
}

function ordenarPorQuantidade(contagens: Map<string, number>): readonly ContagemPorMunicipio[] {
  return [...contagens.entries()]
    .map(([municipio, quantidade]) => ({ municipio, quantidade }))
    .sort((a, b) => b.quantidade - a.quantidade || a.municipio.localeCompare(b.municipio));
}

function arredondar(valor: number): number {
  return Math.round(valor * 100) / 100;
}
