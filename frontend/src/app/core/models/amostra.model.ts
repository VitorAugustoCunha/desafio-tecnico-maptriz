/** Resultado da geracao de massa de teste. */
export interface Amostra {
  /** Quantidade pedida, ja ajustada ao teto configurado no servidor. */
  readonly solicitados: number;
  readonly criados: number;
  /** Lotes pulados por ja haver imovel ocupando aquele espaco. */
  readonly ignorados: number;
  readonly municipio: string;
  readonly bairro: string;
  /** Centro do conjunto gerado — serve para levar o mapa ate onde os lotes estao. */
  readonly latitude: number;
  readonly longitude: number;
}
