import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { routes } from '../../app.routes';
import { APP_CONFIG, CONFIG_PADRAO } from '../../core/config/app-config';
import { Imovel, ImovelListItem } from '../../core/models/imovel.model';
import { Pagina } from '../../core/models/pagina.model';
import { ImoveisStore } from '../../core/state/imoveis.store';
import { ImovelFormComponent } from './form/imovel-form.component';

/**
 * REQUISITO 3 DO DESAFIO.
 *
 * <p>"Ao voltar da edicao para a listagem, nao pode haver uma nova requisicao.
 * A listagem deve reaproveitar os dados que ja estavam em memoria."
 *
 * <p>O teste navega de verdade pelas rotas — listagem, edicao, salvar, voltar —
 * e prova pelo {@link HttpTestingController} que nenhum GET da listagem foi
 * emitido no retorno. Nao verifica um detalhe interno do store: verifica o que
 * realmente saiu pela rede, que e o que o requisito cobra.
 */
describe('requisito 3: voltar da edicao nao dispara novo GET da listagem', () => {
  let httpMock: HttpTestingController;
  let harness: RouterTestingHarness;

  const listaOriginal: ImovelListItem[] = [
    {
      id: 1,
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
    },
    {
      id: 2,
      proprietarioId: 11,
      proprietarioNome: 'João Carlos Ferreira',
      municipio: 'São Paulo',
      uf: 'SP',
      bairro: 'Santana',
      rua: 'Avenida Braz Leme',
      numero: '890',
      latitude: -23.501,
      longitude: -46.628,
      areaM2: 450,
      ativo: true,
    },
  ];

  const paginaDaListagem: Pagina<ImovelListItem> = {
    conteudo: listaOriginal,
    pagina: 0,
    tamanho: 20,
    totalDeElementos: 2,
    totalDePaginas: 1,
    primeira: true,
    ultima: true,
  };

  const imovelParaEditar: Imovel = {
    id: 2,
    proprietario: { id: 11, nome: 'João Carlos Ferreira' },
    municipio: 'São Paulo',
    uf: 'SP',
    bairro: 'Santana',
    rua: 'Avenida Braz Leme',
    numero: '890',
    latitude: -23.501,
    longitude: -46.628,
    areaM2: 450,
    larguraM: null,
    comprimentoM: null,
    possuiGeometria: false,
    geometria: null,
    ativo: true,
    criadoEm: '2026-01-01T00:00:00Z',
    atualizadoEm: '2026-01-01T00:00:00Z',
  };

  const imovelSalvo: Imovel = { ...imovelParaEditar, municipio: 'Campinas', areaM2: 777 };

  /** Conta quantos GET da listagem foram emitidos ate agora. */
  const getsDaListagem = (): number =>
    httpMock.match((r) => r.method === 'GET' && r.url === '/api/imoveis').length;

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

  it('carrega a listagem, edita, salva e volta sem refazer o GET', async () => {
    // --- 1. listagem: primeiro e unico GET esperado -------------------------
    await harness.navigateByUrl('/imoveis');

    const primeiraCarga = httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/imoveis');
    primeiraCarga.flush(paginaDaListagem);
    harness.detectChanges();

    const store = TestBed.inject(ImoveisStore);
    expect(store.itens()).toHaveLength(2);

    // --- 2. edicao: busca o imovel (permitido: e outro recurso) -------------
    const formulario = await harness.navigateByUrl('/imoveis/2/editar', ImovelFormComponent);

    const cargaDoImovel = httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/imoveis/2');
    cargaDoImovel.flush(imovelParaEditar);
    harness.detectChanges();

    expect(formulario.form.controls.municipio.value).toBe('São Paulo');

    // --- 3. salvar ----------------------------------------------------------
    formulario.form.controls.municipio.setValue('Campinas');
    formulario.form.controls.areaM2.setValue(777);
    formulario.salvar();

    const put = httpMock.expectOne((r) => r.method === 'PUT' && r.url === '/api/imoveis/2');
    put.flush(imovelSalvo);

    // O PUT devolve o recurso atualizado; e com ele que o cache e corrigido.
    await harness.fixture.whenStable();
    harness.detectChanges();

    // --- 4. de volta na listagem: NENHUM GET novo ---------------------------
    expect(getsDaListagem()).toBe(0);

    httpMock.expectNone(
      (r) => r.method === 'GET' && r.url === '/api/imoveis',
    );

    // E os dados exibidos ja refletem a edicao, vindos do cache corrigido.
    const itens = store.itens();
    expect(itens).toHaveLength(2);
    expect(itens[1]?.municipio).toBe('Campinas');
    expect(itens[1]?.areaM2).toBe(777);
    expect(itens[0]?.municipio).toBe('Curitiba');

    httpMock.verify();
  });

  it('preserva os filtros ao voltar, mantendo a mesma consulta em cache', async () => {
    // Listagem filtrada e na segunda pagina.
    await harness.navigateByUrl('/imoveis?municipio=Curitiba&pagina=1');

    const comFiltro = httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/imoveis');
    expect(comFiltro.request.params.get('municipio')).toBe('Curitiba');
    expect(comFiltro.request.params.get('pagina')).toBe('1');
    comFiltro.flush(paginaDaListagem);
    harness.detectChanges();

    // Edita preservando a query string, como faz o link "Editar" da tabela.
    const formulario = await harness.navigateByUrl(
      '/imoveis/2/editar?municipio=Curitiba&pagina=1',
      ImovelFormComponent,
    );
    httpMock.expectOne((r) => r.url === '/api/imoveis/2').flush(imovelParaEditar);
    harness.detectChanges();

    formulario.form.controls.areaM2.setValue(500);
    formulario.salvar();
    httpMock.expectOne((r) => r.method === 'PUT').flush(imovelSalvo);

    await harness.fixture.whenStable();
    harness.detectChanges();

    // Mesma consulta de antes: continua em cache, nenhum GET novo.
    expect(getsDaListagem()).toBe(0);
    httpMock.verify();
  });

  it('cadastrar invalida o cache: a listagem precisa ser consultada de novo', async () => {
    await harness.navigateByUrl('/imoveis');
    httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/imoveis').flush(paginaDaListagem);
    harness.detectChanges();

    const formulario = await harness.navigateByUrl('/imoveis/novo', ImovelFormComponent);

    formulario.form.setValue({
      proprietarioNome: 'Ana Beatriz Lima',
      municipio: 'Recife',
      uf: 'PE',
      bairro: 'Boa Viagem',
      rua: 'Avenida Boa Viagem',
      numero: '4500',
      latitude: -8.12,
      longitude: -34.9,
      areaM2: 198.4,
      larguraM: null,
      comprimentoM: null,
      geometria: null,
      ativo: true,
    });

    formulario.salvar();
    httpMock.expectOne((r) => r.method === 'POST' && r.url === '/api/imoveis').flush({
      ...imovelSalvo,
      id: 3,
      proprietario: { id: 12, nome: 'Ana Beatriz Lima' },
    });

    await harness.fixture.whenStable();
    harness.detectChanges();

    // Criar muda a composicao das paginas: aqui uma nova consulta E esperada.
    const recarga = httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/imoveis');
    recarga.flush(paginaDaListagem);

    httpMock.verify();
  });
});
