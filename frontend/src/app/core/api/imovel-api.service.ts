import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_CONFIG } from '../config/app-config';
import { ConsultaImoveis, Imovel, ImovelListItem, ImovelRequest } from '../models/imovel.model';
import { Pagina } from '../models/pagina.model';

/**
 * Acesso HTTP a API de imoveis.
 *
 * <p>Componente nenhum chama `HttpClient` direto: no codigo original isso
 * espalhava URL literal por seis pontos do componente e tornava a tela
 * impossivel de testar sem servidor.
 */
@Injectable({ providedIn: 'root' })
export class ImovelApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/imoveis`;

  /**
   * Pagina da listagem.
   *
   * <p>Filtros vazios nao viram parametro: `?municipio=` faria o servidor
   * montar um LIKE inutil e sujaria a URL.
   */
  listar(consulta: ConsultaImoveis): Observable<Pagina<ImovelListItem>> {
    let params = new HttpParams()
      .set('pagina', consulta.pagina)
      .set('tamanho', consulta.tamanho)
      .set('ordenarPor', consulta.ordenarPor)
      .set('direcao', consulta.direcao);

    if (consulta.proprietarioId !== null) {
      params = params.set('proprietarioId', consulta.proprietarioId);
    }
    if (consulta.proprietarioNome.trim()) {
      params = params.set('proprietarioNome', consulta.proprietarioNome.trim());
    }
    if (consulta.municipio.trim()) {
      params = params.set('municipio', consulta.municipio.trim());
    }

    return this.http.get<Pagina<ImovelListItem>>(this.base, { params });
  }

  buscar(id: number): Observable<Imovel> {
    return this.http.get<Imovel>(`${this.base}/${id}`);
  }

  criar(corpo: ImovelRequest): Observable<Imovel> {
    return this.http.post<Imovel>(this.base, corpo);
  }

  /** Devolve o imovel ja atualizado — e o que o store usa para corrigir o cache sem refazer o GET. */
  atualizar(id: number, corpo: ImovelRequest): Observable<Imovel> {
    return this.http.put<Imovel>(`${this.base}/${id}`, corpo);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
