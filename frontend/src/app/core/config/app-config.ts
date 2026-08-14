import { InjectionToken } from '@angular/core';

/**
 * Configuracao da aplicacao.
 *
 * <p>No codigo original, `http://localhost:8080` aparecia literal em seis pontos
 * do componente — a aplicacao nao subia em nenhum ambiente sem editar codigo.
 *
 * <p>Aqui a base da API e um caminho relativo (`/api`). Em desenvolvimento, o
 * proxy do dev-server encaminha para o backend; em producao, o Nginx faz o mesmo.
 * Como resultado, frontend e API ficam na mesma origem e CORS deixa de existir.
 */
export interface AppConfig {
  readonly apiBaseUrl: string;
  /** Tempo, em ms, que uma pagina da listagem continua valida em memoria. */
  readonly tempoDeCacheDaListagem: number;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export const CONFIG_PADRAO: AppConfig = {
  apiBaseUrl: '/api',
  // 5 minutos: longo o bastante para que nenhum fluxo de navegacao dispare
  // recarga (ir para a edicao e voltar leva segundos), e curto o bastante para
  // que uma aba esquecida aberta nao mostre dados de uma hora atras.
  tempoDeCacheDaListagem: 5 * 60 * 1000,
};
