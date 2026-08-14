import { describe, expect, it } from 'vitest';

import { criarImovelForm, formParaRequest, preencherForm } from './imovel-form.model';
import { Imovel } from '../../../core/models/imovel.model';

describe('formulario de imovel', () => {
  const preencherValido = (): ReturnType<typeof criarImovelForm> => {
    const form = criarImovelForm();
    form.setValue({
      proprietarioNome: 'Maria Souza',
      municipio: 'Curitiba',
      uf: 'PR',
      bairro: 'Batel',
      rua: 'Avenida do Batel',
      numero: '1560',
      latitude: -25.442,
      longitude: -49.292,
      areaM2: 390,
      larguraM: null,
      comprimentoM: null,
      geometria: null,
      ativo: true,
    });
    return form;
  };

  it('aceita um imovel valido', () => {
    expect(preencherValido().valid).toBe(true);
  });

  it('exige os campos obrigatorios', () => {
    const form = criarImovelForm();

    expect(form.controls.proprietarioNome.hasError('required')).toBe(true);
    expect(form.controls.municipio.hasError('required')).toBe(true);
    expect(form.controls.latitude.hasError('required')).toBe(true);
    expect(form.controls.longitude.hasError('required')).toBe(true);
  });

  it('recusa latitude e longitude fora da faixa', () => {
    const form = preencherValido();

    form.controls.latitude.setValue(999);
    expect(form.controls.latitude.hasError('max')).toBe(true);

    form.controls.latitude.setValue(-91);
    expect(form.controls.latitude.hasError('min')).toBe(true);

    form.controls.longitude.setValue(-181);
    expect(form.controls.longitude.hasError('min')).toBe(true);
  });

  it('recusa UF que nao seja duas letras', () => {
    const form = preencherValido();

    form.controls.uf.setValue('XXXXX');
    expect(form.controls.uf.hasError('pattern')).toBe(true);

    form.controls.uf.setValue('pr');
    expect(form.controls.uf.valid).toBe(true);
  });

  it('recusa area zero ou negativa', () => {
    const form = preencherValido();

    form.controls.areaM2.setValue(0);
    expect(form.controls.areaM2.hasError('maiorQueZero')).toBe(true);

    form.controls.areaM2.setValue(-10);
    expect(form.controls.areaM2.hasError('maiorQueZero')).toBe(true);
  });

  it('exige largura e comprimento em par', () => {
    const form = preencherValido();

    form.controls.larguraM.setValue(20);
    expect(form.hasError('dimensoesEmPar')).toBe(true);

    form.controls.comprimentoM.setValue(50);
    expect(form.hasError('dimensoesEmPar')).toBe(false);
  });

  it('exige area quando nao ha dimensoes', () => {
    const form = preencherValido();

    form.controls.areaM2.setValue(null);
    expect(form.hasError('tamanhoInformavel')).toBe(true);

    form.controls.larguraM.setValue(20);
    form.controls.comprimentoM.setValue(50);
    expect(form.hasError('tamanhoInformavel')).toBe(false);
  });

  describe('formParaRequest', () => {
    it('normaliza UF para maiuscula e recorta espacos', () => {
      const form = preencherValido();
      form.controls.uf.setValue(' pr ');
      form.controls.municipio.setValue('  Curitiba  ');

      const corpo = formParaRequest(form);

      expect(corpo.uf).toBe('PR');
      expect(corpo.municipio).toBe('Curitiba');
    });

    it('omite a area quando ha dimensoes: quem calcula e o servidor', () => {
      const form = preencherValido();
      form.controls.larguraM.setValue(20);
      form.controls.comprimentoM.setValue(50);

      const corpo = formParaRequest(form);

      expect(corpo.areaM2).toBeNull();
      expect(corpo.larguraM).toBe(20);
      expect(corpo.comprimentoM).toBe(50);
    });

    it('mantem a area quando nao ha dimensoes', () => {
      const corpo = formParaRequest(preencherValido());

      expect(corpo.areaM2).toBe(390);
      expect(corpo.larguraM).toBeNull();
    });
  });

  describe('preencherForm', () => {
    it('copia os valores do imovel sem guardar referencia ao objeto', () => {
      const imovel: Imovel = {
        id: 1,
        proprietario: { id: 10, nome: 'Maria Souza' },
        municipio: 'Curitiba',
        uf: 'PR',
        bairro: 'Batel',
        rua: 'Av. do Batel',
        numero: '1560',
        latitude: -25.442,
        longitude: -49.292,
        areaM2: 390,
        larguraM: null,
        comprimentoM: null,
        possuiGeometria: false,
        geometria: null,
        ativo: true,
        criadoEm: '2026-01-01T00:00:00Z',
        atualizadoEm: '2026-01-01T00:00:00Z',
      };

      const form = criarImovelForm();
      preencherForm(form, imovel);

      expect(form.controls.proprietarioNome.value).toBe('Maria Souza');
      expect(form.controls.municipio.value).toBe('Curitiba');

      // Alterar o formulario NAO altera o imovel de origem: era exatamente o
      // defeito do codigo original (`this.form = i`).
      form.controls.municipio.setValue('Outro');
      expect(imovel.municipio).toBe('Curitiba');
    });
  });
});
