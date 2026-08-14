import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_CONFIG } from '../config/app-config';
import { Pagina } from '../models/pagina.model';
import { Proprietario, ProprietarioListItem } from '../models/proprietario.model';

@Injectable({ providedIn: 'root' })
export class ProprietarioApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/proprietarios`;

  listar(busca: string, pagina: number, tamanho: number): Observable<Pagina<ProprietarioListItem>> {
    let params = new HttpParams().set('pagina', pagina).set('tamanho', tamanho);

    if (busca.trim()) {
      params = params.set('busca', busca.trim());
    }

    return this.http.get<Pagina<ProprietarioListItem>>(this.base, { params });
  }

  buscar(id: number): Observable<Proprietario> {
    return this.http.get<Proprietario>(`${this.base}/${id}`);
  }

  /**
   * Renomeia o titular (requisito 5).
   *
   * <p>Uma unica chamada: os imoveis apontam para o id, entao nao ha nada para
   * atualizar em lote no cliente.
   */
  renomear(id: number, nome: string): Observable<Proprietario> {
    return this.http.put<Proprietario>(`${this.base}/${id}`, { nome });
  }

  criar(nome: string): Observable<Proprietario> {
    return this.http.post<Proprietario>(this.base, { nome });
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
