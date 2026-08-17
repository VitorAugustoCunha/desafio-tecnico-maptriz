import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_CONFIG } from '../config/app-config';
import { Amostra } from '../models/amostra.model';

/**
 * Geracao de massa de teste.
 *
 * <p>Uma requisicao so, e nao mil `POST /api/imoveis`: alem de levar minutos, o
 * caminho normal toma o advisory lock de geometria uma vez por imovel, entao
 * mil cadastros isolados seriam mil filas — e qualquer recusa no meio deixaria
 * a carga pela metade.
 */
@Injectable({ providedIn: 'root' })
export class AmostraApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/amostra`;

  gerarImoveis(quantidade: number): Observable<Amostra> {
    const params = new HttpParams().set('quantidade', quantidade);
    return this.http.post<Amostra>(`${this.base}/imoveis`, null, { params });
  }
}
