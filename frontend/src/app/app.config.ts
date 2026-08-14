import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';

import { APP_CONFIG, CONFIG_PADRAO } from './core/config/app-config';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),

    // Este projeto nao usa zone.js. Sem ela, a deteccao de mudancas depende de
    // signals — que e o motivo de todo estado da aplicacao viver em signal, e de
    // nao existir nenhum `detectChanges()` manual como no codigo original.
    provideZonelessChangeDetection(),

    provideRouter(
      routes,
      // Liga `data` e parametros de rota diretamente aos inputs do componente.
      withComponentInputBinding(),
      // Trocar de pagina volta ao topo; voltar pelo historico restaura a posicao.
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),

    provideHttpClient(withFetch()),

    { provide: APP_CONFIG, useValue: CONFIG_PADRAO },
  ],
};
