import { describe, expect, it } from 'vitest';

import { CONSULTA_PADRAO, ConsultaImoveis, chaveDaConsulta } from '../models/imovel.model';
import { consultaParaQueryParams, queryParamsParaConsulta } from './consulta-params';

describe('consulta <-> query params', () => {
  it('nao poe na URL o que e valor padrao', () => {
    expect(consultaParaQueryParams(CONSULTA_PADRAO)).toEqual({});
  });

  it('serializa apenas o que foge do padrao', () => {
    const consulta: ConsultaImoveis = {
      ...CONSULTA_PADRAO,
      municipio: 'Curitiba',
      pagina: 2,
      ordenarPor: 'area',
      direcao: 'desc',
    };

    expect(consultaParaQueryParams(consulta)).toEqual({
      municipio: 'Curitiba',
      pagina: 2,
      ordenarPor: 'area',
      direcao: 'desc',
    });
  });

  it('faz ida e volta sem perder informacao', () => {
    const consulta: ConsultaImoveis = {
      proprietarioId: 42,
      proprietarioNome: 'Maria',
      municipio: 'Curitiba',
      pagina: 3,
      tamanho: 50,
      ordenarPor: 'municipio',
      direcao: 'desc',
    };

    const voltou = queryParamsParaConsulta(consultaParaQueryParams(consulta));

    expect(voltou).toEqual(consulta);
    expect(chaveDaConsulta(voltou)).toBe(chaveDaConsulta(consulta));
  });

  it('ignora valor invalido em vez de quebrar a tela', () => {
    const consulta = queryParamsParaConsulta({
      pagina: 'abc',
      tamanho: 'muito',
      ordenarPor: 'senha',
      direcao: 'lateral',
    });

    expect(consulta.pagina).toBe(CONSULTA_PADRAO.pagina);
    expect(consulta.tamanho).toBe(CONSULTA_PADRAO.tamanho);
    expect(consulta.ordenarPor).toBe(CONSULTA_PADRAO.ordenarPor);
    expect(consulta.direcao).toBe(CONSULTA_PADRAO.direcao);
  });

  it('recusa pagina negativa e limita o tamanho', () => {
    expect(queryParamsParaConsulta({ pagina: -5 }).pagina).toBe(0);
    expect(queryParamsParaConsulta({ tamanho: 100000 }).tamanho).toBe(100);
    expect(queryParamsParaConsulta({ tamanho: 1 }).tamanho).toBe(5);
  });

  it('so aceita ordenacao da whitelist', () => {
    expect(queryParamsParaConsulta({ ordenarPor: 'municipio' }).ordenarPor).toBe('municipio');
    expect(queryParamsParaConsulta({ ordenarPor: 'proprietario' }).ordenarPor).toBe('proprietario');
    expect(queryParamsParaConsulta({ ordenarPor: 'dropTable' }).ordenarPor).toBe('id');
  });
});

describe('chave da consulta', () => {
  it('e igual para consultas equivalentes', () => {
    const a = chaveDaConsulta({ ...CONSULTA_PADRAO, municipio: 'Curitiba' });
    const b = chaveDaConsulta({ ...CONSULTA_PADRAO, municipio: '  CURITIBA  ' });

    expect(a).toBe(b);
  });

  it('muda quando qualquer parametro relevante muda', () => {
    const base = chaveDaConsulta(CONSULTA_PADRAO);

    expect(chaveDaConsulta({ ...CONSULTA_PADRAO, pagina: 1 })).not.toBe(base);
    expect(chaveDaConsulta({ ...CONSULTA_PADRAO, tamanho: 50 })).not.toBe(base);
    expect(chaveDaConsulta({ ...CONSULTA_PADRAO, ordenarPor: 'area' })).not.toBe(base);
    expect(chaveDaConsulta({ ...CONSULTA_PADRAO, direcao: 'desc' })).not.toBe(base);
    expect(chaveDaConsulta({ ...CONSULTA_PADRAO, municipio: 'x' })).not.toBe(base);
    expect(chaveDaConsulta({ ...CONSULTA_PADRAO, proprietarioId: 1 })).not.toBe(base);
  });
});
