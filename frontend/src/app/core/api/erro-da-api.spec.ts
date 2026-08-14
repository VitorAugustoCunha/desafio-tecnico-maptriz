import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { traduzirErro } from './erro-da-api';

describe('traducao de erro da API', () => {
  const respostaComProblema = (status: number, corpo: unknown): HttpErrorResponse =>
    new HttpErrorResponse({ status, error: corpo, url: '/api/imoveis' });

  it('usa o detail do ProblemDetail como mensagem', () => {
    const erro = traduzirErro(
      respostaComProblema(409, {
        type: 'urn:webgis:problema:conflito-espacial',
        title: 'Conflito espacial',
        status: 409,
        detail: 'A area informada conflita com o imovel 12 ja cadastrado',
        idImovelConflitante: 12,
      }),
    );

    expect(erro.mensagem).toContain('conflita com o imovel 12');
    expect(erro.tipo).toBe('urn:webgis:problema:conflito-espacial');
    expect(erro.idImovelConflitante).toBe(12);
  });

  it('indexa os erros por campo para o formulario destacar o input certo', () => {
    const erro = traduzirErro(
      respostaComProblema(400, {
        type: 'urn:webgis:problema:validacao',
        title: 'Dados invalidos',
        status: 400,
        detail: 'Um ou mais campos sao invalidos.',
        erros: [
          { campo: 'latitude', mensagem: 'a latitude deve estar entre -90 e 90' },
          { campo: 'uf', mensagem: 'use a sigla de 2 letras' },
        ],
      }),
    );

    expect(erro.errosPorCampo.get('latitude')).toBe('a latitude deve estar entre -90 e 90');
    expect(erro.errosPorCampo.get('uf')).toBe('use a sigla de 2 letras');
  });

  it('mantem apenas a primeira mensagem de cada campo', () => {
    const erro = traduzirErro(
      respostaComProblema(400, {
        detail: 'invalido',
        erros: [
          { campo: 'uf', mensagem: 'primeira' },
          { campo: 'uf', mensagem: 'segunda' },
        ],
      }),
    );

    expect(erro.errosPorCampo.get('uf')).toBe('primeira');
  });

  it('reconhece falha de rede (status 0) com mensagem propria', () => {
    const erro = traduzirErro(new HttpErrorResponse({ status: 0 }));

    expect(erro.status).toBe(0);
    expect(erro.tipo).toBe('rede');
    expect(erro.mensagem).toContain('conexão');
  });

  it('usa mensagem padrao quando a resposta nao traz ProblemDetail', () => {
    expect(traduzirErro(respostaComProblema(404, null)).mensagem).toBe('Registro não encontrado.');
    expect(traduzirErro(respostaComProblema(503, null)).mensagem).toContain('temporariamente indisponível');
  });

  it('trata erro que nem e HttpErrorResponse', () => {
    const erro = traduzirErro(new Error('coisa estranha'));

    expect(erro.status).toBe(0);
    expect(erro.errosPorCampo.size).toBe(0);
  });
});
