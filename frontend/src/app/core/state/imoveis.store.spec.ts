import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { APP_CONFIG, CONFIG_PADRAO } from '../config/app-config';
import { CONSULTA_PADRAO, ConsultaImoveis, Imovel, ImovelListItem } from '../models/imovel.model';
import { Pagina } from '../models/pagina.model';
import { ImoveisStore } from './imoveis.store';

/** Testes da politica de cache da listagem — o coracao do requisito 3. */
describe('ImoveisStore', () => {
  let store: ImoveisStore;
  let httpMock: HttpTestingController;

  const item = (id: number, extras: Partial<ImovelListItem> = {}): ImovelListItem => ({
    id,
    proprietarioId: 10,
    proprietarioNome: 'Maria Souza',
    municipio: 'Curitiba',
    uf: 'PR',
    bairro: 'Batel',
    rua: 'Avenida do Batel',
    numero: '1560',
    latitude: -25.442,
    longitude: -49.292,
    areaM2: 100,
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

  const responderListagem = (itens: ImovelListItem[]): void => {
    const requisicao = httpMock.expectOne((r) => r.url === '/api/imoveis' && r.method === 'GET');
    requisicao.flush(pagina(itens));
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: CONFIG_PADRAO },
      ],
    });

    store = TestBed.inject(ImoveisStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('busca no servidor quando o cache esta vazio', () => {
    store.garantirCarregado(CONSULTA_PADRAO);

    responderListagem([item(1), item(2)]);

    expect(store.itens()).toHaveLength(2);
    expect(store.carregando()).toBe(false);
    httpMock.verify();
  });

  it('NAO emite nova requisicao quando a mesma consulta ja esta em cache', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1)]);

    // Segunda chamada com os mesmos parametros: e o caso de voltar da edicao.
    store.garantirCarregado(CONSULTA_PADRAO);

    httpMock.expectNone((r) => r.url === '/api/imoveis');
    expect(store.itens()).toHaveLength(1);
  });

  it('busca de novo quando o filtro muda', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1)]);

    store.garantirCarregado({ ...CONSULTA_PADRAO, municipio: 'Curitiba' });

    httpMock.expectOne((r) => r.url === '/api/imoveis').flush(pagina([item(2)]));
    expect(store.itens()[0]?.id).toBe(2);
  });

  it('busca de novo quando a pagina muda', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1)]);

    store.garantirCarregado({ ...CONSULTA_PADRAO, pagina: 1 });

    httpMock.expectOne((r) => r.url === '/api/imoveis').flush(pagina([item(3)]));
  });

  it('busca de novo quando a ordenacao muda', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1)]);

    store.garantirCarregado({ ...CONSULTA_PADRAO, ordenarPor: 'municipio' });

    httpMock.expectOne((r) => r.url === '/api/imoveis').flush(pagina([item(1)]));
  });

  it('trata filtro equivalente (caixa e espacos) como a mesma consulta', () => {
    const comFiltro: ConsultaImoveis = { ...CONSULTA_PADRAO, municipio: 'Curitiba' };

    store.garantirCarregado(comFiltro);
    responderListagem([item(1)]);

    store.garantirCarregado({ ...CONSULTA_PADRAO, municipio: '  curitiba  ' });

    httpMock.expectNone((r) => r.url === '/api/imoveis');
  });

  it('aplica o imovel atualizado no cache sem tocar na rede', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1), item(2)]);

    const atualizado: Imovel = {
      id: 2,
      proprietario: { id: 10, nome: 'Maria Souza Ferreira' },
      municipio: 'Campinas',
      uf: 'SP',
      bairro: 'Centro',
      rua: 'Rua Nova',
      numero: '99',
      latitude: -22.9,
      longitude: -47.06,
      areaM2: 555,
      larguraM: null,
      comprimentoM: null,
      possuiGeometria: false,
      geometria: null,
      ativo: true,
      criadoEm: '2026-01-01T00:00:00Z',
      atualizadoEm: '2026-01-02T00:00:00Z',
    };

    store.aplicarImovelAtualizado(atualizado);

    httpMock.expectNone((r) => r.url === '/api/imoveis');

    const itens = store.itens();
    expect(itens[1]?.municipio).toBe('Campinas');
    expect(itens[1]?.areaM2).toBe(555);
    expect(itens[1]?.proprietarioNome).toBe('Maria Souza Ferreira');
    // O item nao editado permanece igual.
    expect(itens[0]?.municipio).toBe('Curitiba');
  });

  it('substitui o item de forma imutavel, sem alterar o objeto anterior', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1)]);

    const antes = store.itens();
    const itemAntes = antes[0];

    store.aplicarImovelAtualizado({
      id: 1,
      proprietario: { id: 10, nome: 'Outro Nome' },
      municipio: 'Santos',
      uf: 'SP',
      bairro: 'Centro',
      rua: 'Rua X',
      numero: '1',
      latitude: -23.96,
      longitude: -46.33,
      areaM2: 10,
      larguraM: null,
      comprimentoM: null,
      possuiGeometria: false,
      geometria: null,
      ativo: true,
      criadoEm: '2026-01-01T00:00:00Z',
      atualizadoEm: '2026-01-02T00:00:00Z',
    });

    // O array e o item antigos continuam intactos: a lista nao foi mutada por
    // referencia, que era o defeito FE-01 do codigo original.
    expect(itemAntes?.municipio).toBe('Curitiba');
    expect(store.itens()).not.toBe(antes);
  });

  it('ignora atualizacao de imovel que nao esta na pagina exibida', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1)]);

    const antes = store.itens();

    store.aplicarImovelAtualizado({
      id: 999,
      proprietario: { id: 10, nome: 'Fulano' },
      municipio: 'Outro',
      uf: 'SP',
      bairro: 'B',
      rua: 'R',
      numero: '1',
      latitude: 0,
      longitude: 0,
      areaM2: 1,
      larguraM: null,
      comprimentoM: null,
      possuiGeometria: false,
      geometria: null,
      ativo: true,
      criadoEm: '2026-01-01T00:00:00Z',
      atualizadoEm: '2026-01-01T00:00:00Z',
    });

    expect(store.itens()).toBe(antes);
  });

  it('invalidar forca nova busca na proxima consulta', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1)]);

    store.invalidar();
    store.garantirCarregado(CONSULTA_PADRAO);

    httpMock.expectOne((r) => r.url === '/api/imoveis').flush(pagina([item(1), item(2)]));
    expect(store.itens()).toHaveLength(2);
  });

  it('expoe erro tratado e derruba o cache quando a busca falha', () => {
    store.garantirCarregado(CONSULTA_PADRAO);

    httpMock
      .expectOne((r) => r.url === '/api/imoveis')
      .flush(
        { type: 'urn:webgis:problema:erro-interno', title: 'Erro interno', status: 500, detail: 'Falhou' },
        { status: 500, statusText: 'Server Error' },
      );

    expect(store.erro()?.mensagem).toBe('Falhou');
    expect(store.carregando()).toBe(false);
    expect(store.emCache(CONSULTA_PADRAO)).toBe(false);
  });

  it('calcula a area total da pagina como valor derivado', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([item(1, { areaM2: 100 }), item(2, { areaM2: 250.5 })]);

    expect(store.areaTotalDaPagina()).toBe(350.5);
  });

  it('marca estado vazio quando a consulta nao retorna nada', () => {
    store.garantirCarregado(CONSULTA_PADRAO);
    responderListagem([]);

    expect(store.vazio()).toBe(true);
    expect(store.erro()).toBeNull();
  });
});
