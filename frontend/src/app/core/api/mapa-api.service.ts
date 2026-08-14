import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_CONFIG } from '../config/app-config';
import { ColecaoDeFeicoes } from '../../features/mapa/gis/gis-protocolo';

/** Retangulo do viewport em WGS 84. */
export interface Viewport {
  readonly minLon: number;
  readonly minLat: number;
  readonly maxLon: number;
  readonly maxLat: number;
}

@Injectable({ providedIn: 'root' })
export class MapaApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/mapa`;

  /**
   * Feicoes dentro do viewport.
   *
   * <p>O bbox e obrigatorio: nao existe chamada que traga todos os imoveis para
   * o mapa. Em zoom afastado o servidor devolve agregados em vez de feicao por
   * feicao.
   */
  noViewport(viewport: Viewport, zoom: number, apenasAtivos = true): Observable<ColecaoDeFeicoes> {
    const params = new HttpParams()
      .set('minLon', viewport.minLon)
      .set('minLat', viewport.minLat)
      .set('maxLon', viewport.maxLon)
      .set('maxLat', viewport.maxLat)
      .set('zoom', Math.round(zoom))
      .set('apenasAtivos', apenasAtivos);

    return this.http.get<ColecaoDeFeicoes>(`${this.base}/imoveis`, { params });
  }
}
