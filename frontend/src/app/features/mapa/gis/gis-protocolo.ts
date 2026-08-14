/**
 * Protocolo de mensagens do Web Worker GIS.
 *
 * <p>Tipado dos dois lados: o worker e a thread principal compartilham estes
 * tipos, entao mudar o formato de uma mensagem quebra a compilacao em vez de
 * virar `undefined` em producao.
 */

/** Feicao GeoJSON como vem da API do mapa. */
export interface FeicaoGeoJson {
  readonly type: 'Feature';
  readonly geometry: GeometriaGeoJson;
  readonly properties: PropriedadesDaFeicao;
}

export type GeometriaGeoJson =
  | { readonly type: 'Point'; readonly coordinates: readonly [number, number] }
  | { readonly type: 'Polygon'; readonly coordinates: readonly (readonly (readonly [number, number])[])[] };

export interface PropriedadesDaFeicao {
  readonly id?: number;
  readonly proprietarioId?: number;
  readonly proprietarioNome?: string;
  readonly municipio?: string;
  readonly uf?: string;
  readonly areaM2?: number;
  readonly poligono?: boolean;
  readonly quantidade?: number;
}

export interface ColecaoDeFeicoes {
  readonly type: 'FeatureCollection';
  readonly features: readonly FeicaoGeoJson[];
  readonly metadados?: {
    readonly zoom: number;
    readonly agregado: boolean;
    readonly total: number;
    readonly truncado: boolean;
    readonly limite: number;
  };
}

// --- mensagens: thread principal -> worker ---------------------------------

export interface PedidoPreparar {
  readonly tipo: 'preparar';
  /** Identifica a resposta. Respostas de pedidos antigos sao descartadas. */
  readonly requestId: number;
  readonly colecao: ColecaoDeFeicoes;
}

export interface PedidoCancelar {
  readonly tipo: 'cancelar';
  readonly requestId: number;
}

export type PedidoAoWorker = PedidoPreparar | PedidoCancelar;

// --- mensagens: worker -> thread principal ---------------------------------

export interface RespostaPronta {
  readonly tipo: 'pronto';
  readonly requestId: number;
  readonly resultado: ResultadoPreparado;
}

export interface RespostaErro {
  readonly tipo: 'erro';
  readonly requestId: number;
  /** Erro serializavel: `Error` nao atravessa `postMessage` com a stack intacta. */
  readonly mensagem: string;
}

export interface RespostaCancelada {
  readonly tipo: 'cancelado';
  readonly requestId: number;
}

export type RespostaDoWorker = RespostaPronta | RespostaErro | RespostaCancelada;

/**
 * Saida do pipeline.
 *
 * <p>`coordenadas` e um `Float64Array` justamente para poder ser transferido
 * (`Transferable`) em vez de copiado: com dezenas de milhares de pontos, a copia
 * estrutural do `postMessage` custa mais que o proprio calculo.
 */
export interface ResultadoPreparado {
  /** Pares [lon, lat] achatados: `[lon0, lat0, lon1, lat1, ...]`. */
  readonly coordenadas: Float64Array;
  readonly pontos: readonly PontoPreparado[];
  readonly poligonos: readonly PoligonoPreparado[];
  readonly estatisticas: EstatisticasDoLote;
  readonly descartadas: number;
}

export interface PontoPreparado {
  readonly id: number | null;
  readonly lon: number;
  readonly lat: number;
  readonly quantidade: number;
  readonly rotulo: string;
  readonly propriedades: PropriedadesDaFeicao;
}

export interface PoligonoPreparado {
  readonly id: number | null;
  readonly anel: readonly (readonly [number, number])[];
  readonly propriedades: PropriedadesDaFeicao;
}

export interface EstatisticasDoLote {
  readonly totalDeFeicoes: number;
  readonly totalDeImoveis: number;
  readonly areaTotalM2: number;
  readonly areaMediaM2: number;
  readonly limites: Limites | null;
  readonly porMunicipio: readonly ContagemPorMunicipio[];
}

export interface ContagemPorMunicipio {
  readonly municipio: string;
  readonly quantidade: number;
}

export interface Limites {
  readonly minLon: number;
  readonly minLat: number;
  readonly maxLon: number;
  readonly maxLat: number;
}
