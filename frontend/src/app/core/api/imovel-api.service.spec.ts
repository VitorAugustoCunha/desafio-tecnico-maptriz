import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { APP_CONFIG, CONFIG_PADRAO } from '../config/app-config';
import { CONSULTA_PADRAO } from '../models/imovel.model';
import { ImovelApiService } from './imovel-api.service';

describe('ImovelApiService', () => {
  let api: ImovelApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: CONFIG_PADRAO },
      ],
    });

    api = TestBed.inject(ImovelApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('usa caminho relativo, nunca URL absoluta com host', () => {
    api.listar(CONSULTA_PADRAO).subscribe();

    const requisicao = httpMock.expectOne((r) => r.url === '/api/imoveis');

    expect(requisicao.request.url).not.toContain('localhost');
    expect(requisicao.request.url).toBe('/api/imoveis');
  });

  it('envia paginacao e ordenacao', () => {
    api.listar({ ...CONSULTA_PADRAO, pagina: 2, tamanho: 50, ordenarPor: 'area', direcao: 'desc' }).subscribe();

    const { params } = httpMock.expectOne((r) => r.url === '/api/imoveis').request;

    expect(params.get('pagina')).toBe('2');
    expect(params.get('tamanho')).toBe('50');
    expect(params.get('ordenarPor')).toBe('area');
    expect(params.get('direcao')).toBe('desc');
  });

  it('omite filtro vazio em vez de mandar parametro em branco', () => {
    api.listar({ ...CONSULTA_PADRAO, municipio: '   ', proprietarioNome: '' }).subscribe();

    const { params } = httpMock.expectOne((r) => r.url === '/api/imoveis').request;

    expect(params.has('municipio')).toBe(false);
    expect(params.has('proprietarioNome')).toBe(false);
    expect(params.has('proprietarioId')).toBe(false);
  });

  it('recorta espacos dos filtros enviados', () => {
    api.listar({ ...CONSULTA_PADRAO, municipio: '  Curitiba  ' }).subscribe();

    const { params } = httpMock.expectOne((r) => r.url === '/api/imoveis').request;
    expect(params.get('municipio')).toBe('Curitiba');
  });

  it('envia proprietarioId quando presente', () => {
    api.listar({ ...CONSULTA_PADRAO, proprietarioId: 42 }).subscribe();

    const { params } = httpMock.expectOne((r) => r.url === '/api/imoveis').request;
    expect(params.get('proprietarioId')).toBe('42');
  });

  it('cria com POST no recurso da colecao', () => {
    const corpo = {
      proprietarioNome: 'Maria Souza',
      municipio: 'Curitiba',
      uf: 'PR',
      bairro: 'Batel',
      rua: 'Av. do Batel',
      numero: '1560',
      latitude: -25.442,
      longitude: -49.292,
      areaM2: 100,
      larguraM: null,
      comprimentoM: null,
      geometria: null,
      ativo: true,
    };

    api.criar(corpo).subscribe();

    const requisicao = httpMock.expectOne((r) => r.method === 'POST' && r.url === '/api/imoveis');
    expect(requisicao.request.body).toEqual(corpo);
  });

  it('atualiza com PUT no recurso individual', () => {
    api
      .atualizar(7, {
        proprietarioNome: 'Maria Souza',
        municipio: 'Campinas',
        uf: 'SP',
        bairro: 'Centro',
        rua: 'Rua A',
        numero: '10',
        latitude: -22.9,
        longitude: -47.06,
        areaM2: 100,
        larguraM: null,
        comprimentoM: null,
        geometria: null,
        ativo: true,
      })
      .subscribe();

    httpMock.expectOne((r) => r.method === 'PUT' && r.url === '/api/imoveis/7');
  });

  it('exclui com DELETE', () => {
    api.excluir(7).subscribe();
    httpMock.expectOne((r) => r.method === 'DELETE' && r.url === '/api/imoveis/7');
  });

  it('busca o detalhe pelo id', () => {
    api.buscar(3).subscribe();
    httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/imoveis/3');
  });
});
