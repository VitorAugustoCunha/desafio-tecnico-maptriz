import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { routes } from '../../../app.routes';
import { APP_CONFIG, CONFIG_PADRAO } from '../../../core/config/app-config';
import { ImovelListItem } from '../../../core/models/imovel.model';
import { Pagina } from '../../../core/models/pagina.model';

describe('ImoveisListaComponent', () => {
  let httpMock: HttpTestingController;
  let harness: RouterTestingHarness;

  const item = (id: number, extras: Partial<ImovelListItem> = {}): ImovelListItem => ({
    id,
    proprietarioId: 10,
    proprietarioNome: 'Maria Aparecida Souza',
    municipio: 'Curitiba',
    uf: 'PR',
    bairro: 'Batel',
    rua: 'Avenida do Batel',
    numero: '1560',
    latitude: -25.442,
    longitude: -49.292,
    areaM2: 390,
    ativo: true,
    ...extras,
  });

  const pagina = (itens: ImovelListItem[]): Pagina<ImovelListItem> => ({
    conteudo: itens,
    pagina: 0,
    tamanho: 20,
    totalDeElementos: itens.length,
    totalDePaginas: 1,
    primeira: true,
    ultima: true,
  });

  const texto = (): string => harness.routeNativeElement?.textContent ?? '';

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: CONFIG_PADRAO },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  it('mostra o estado de carregando antes da resposta', async () => {
    await harness.navigateByUrl('/imoveis');
    harness.detectChanges();

    expect(texto()).toContain('Carregando imóveis');
  });

  it('renderiza as linhas depois da resposta', async () => {
    await harness.navigateByUrl('/imoveis');
    httpMock.expectOne((r) => r.url === '/api/imoveis').flush(pagina([item(1), item(2, { municipio: 'Campinas' })]));
    harness.detectChanges();

    const conteudo = texto();
    expect(conteudo).toContain('Maria Aparecida Souza');
    expect(conteudo).toContain('Curitiba');
    expect(conteudo).toContain('Campinas');
    expect(conteudo).not.toContain('Carregando imóveis');
  });

  it('mostra estado vazio quando nao ha imoveis', async () => {
    await harness.navigateByUrl('/imoveis');
    httpMock.expectOne((r) => r.url === '/api/imoveis').flush(pagina([]));
    harness.detectChanges();

    expect(texto()).toContain('Nenhum imóvel cadastrado ainda');
  });

  it('diferencia estado vazio com filtro aplicado', async () => {
    await harness.navigateByUrl('/imoveis?municipio=Inexistente');
    httpMock.expectOne((r) => r.url === '/api/imoveis').flush(pagina([]));
    harness.detectChanges();

    expect(texto()).toContain('Nenhum imóvel corresponde aos filtros');
  });

  it('mostra estado de erro, distinto do estado vazio', async () => {
    await harness.navigateByUrl('/imoveis');
    httpMock
      .expectOne((r) => r.url === '/api/imoveis')
      .flush(
        { detail: 'Banco indisponivel', status: 503 },
        { status: 503, statusText: 'Service Unavailable' },
      );
    harness.detectChanges();

    const conteudo = texto();
    expect(conteudo).toContain('Não foi possível carregar a listagem');
    expect(conteudo).toContain('Banco indisponivel');
    expect(conteudo).toContain('Tentar novamente');
  });

  it('aplica o filtro de municipio vindo da URL na requisicao', async () => {
    await harness.navigateByUrl('/imoveis?municipio=Curitiba&proprietarioNome=Maria');

    const requisicao = httpMock.expectOne((r) => r.url === '/api/imoveis');

    expect(requisicao.request.params.get('municipio')).toBe('Curitiba');
    expect(requisicao.request.params.get('proprietarioNome')).toBe('Maria');
  });

  it('exibe a area total da pagina como valor derivado', async () => {
    await harness.navigateByUrl('/imoveis');
    httpMock
      .expectOne((r) => r.url === '/api/imoveis')
      .flush(pagina([item(1, { areaM2: 100 }), item(2, { areaM2: 250.5 })]));
    harness.detectChanges();

    expect(texto()).toContain('350.50');
  });

  it('marca a coluna ordenada com aria-sort para leitor de tela', async () => {
    await harness.navigateByUrl('/imoveis?ordenarPor=municipio&direcao=desc');
    httpMock.expectOne((r) => r.url === '/api/imoveis').flush(pagina([item(1)]));
    harness.detectChanges();

    const cabecalho = harness.routeNativeElement?.querySelector('th[aria-sort="descending"]');
    expect(cabecalho?.textContent).toContain('Município');
  });
});
