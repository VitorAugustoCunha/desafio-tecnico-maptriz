import { FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import { Imovel, ImovelRequest, PoligonoGeoJson } from '../../../core/models/imovel.model';

/**
 * Formulario tipado do imovel.
 *
 * <p>Cada validador aqui espelha uma regra do backend. A validacao do cliente
 * existe para dar resposta imediata, nao para substituir a do servidor — o
 * backend valida de novo, sempre, porque a API tambem e chamada por fora da tela.
 */
export type ImovelFormGroup = FormGroup<{
  proprietarioNome: FormControl<string>;
  municipio: FormControl<string>;
  uf: FormControl<string>;
  bairro: FormControl<string>;
  rua: FormControl<string>;
  numero: FormControl<string>;
  latitude: FormControl<number | null>;
  longitude: FormControl<number | null>;
  areaM2: FormControl<number | null>;
  larguraM: FormControl<number | null>;
  comprimentoM: FormControl<number | null>;
  /** Lote desenhado no mapa. Excludente com largura/comprimento. */
  geometria: FormControl<PoligonoGeoJson | null>;
  ativo: FormControl<boolean>;
}>;

export function criarImovelForm(): ImovelFormGroup {
  return new FormGroup(
    {
      proprietarioNome: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(120)],
      }),
      municipio: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(120)],
      }),
      uf: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)],
      }),
      bairro: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(100)],
      }),
      rua: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(150)],
      }),
      numero: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(10)],
      }),
      latitude: new FormControl<number | null>(null, {
        validators: [Validators.required, Validators.min(-90), Validators.max(90)],
      }),
      longitude: new FormControl<number | null>(null, {
        validators: [Validators.required, Validators.min(-180), Validators.max(180)],
      }),
      areaM2: new FormControl<number | null>(null, { validators: [maiorQueZero()] }),
      larguraM: new FormControl<number | null>(null, { validators: [maiorQueZero()] }),
      comprimentoM: new FormControl<number | null>(null, { validators: [maiorQueZero()] }),
      geometria: new FormControl<PoligonoGeoJson | null>(null),
      ativo: new FormControl(true, { nonNullable: true }),
    },
    { validators: [dimensoesEmPar(), tamanhoInformavel(), formaUnica()] },
  );
}

/**
 * Dimensões e desenho são excludentes — espelha o `isFormaUnica` do backend.
 *
 * <p>Aceitar as duas obrigaria a eleger uma vencedora em silêncio, e o usuário
 * descobriria qual foi olhando o mapa depois de salvar.
 */
export function formaUnica(): ValidatorFn {
  return (grupo): ValidationErrors | null => {
    const temDimensoes =
      preenchido(grupo.get('larguraM')?.value) && preenchido(grupo.get('comprimentoM')?.value);
    const temDesenho = grupo.get('geometria')?.value != null;

    return temDimensoes && temDesenho ? { formaUnica: true } : null;
  };
}

/** Aceita vazio (o campo pode ser opcional), mas nao aceita zero nem negativo. */
export function maiorQueZero(): ValidatorFn {
  return (controle): ValidationErrors | null => {
    const valor = controle.value;
    if (valor === null || valor === undefined || valor === '') {
      return null;
    }
    return Number(valor) > 0 ? null : { maiorQueZero: true };
  };
}

/**
 * Largura e comprimento andam juntos: meia dimensao nao monta retangulo.
 * Espelha o `@AssertTrue isDimensoesEmPar` do backend.
 */
export function dimensoesEmPar(): ValidatorFn {
  return (grupo): ValidationErrors | null => {
    const largura = grupo.get('larguraM')?.value;
    const comprimento = grupo.get('comprimentoM')?.value;

    const temLargura = preenchido(largura);
    const temComprimento = preenchido(comprimento);

    return temLargura === temComprimento ? null : { dimensoesEmPar: true };
  };
}

/** Sem dimensoes e sem desenho, a area precisa vir preenchida. Espelha `isAreaInformavel`. */
export function tamanhoInformavel(): ValidatorFn {
  return (grupo): ValidationErrors | null => {
    const area = grupo.get('areaM2')?.value;
    const largura = grupo.get('larguraM')?.value;
    const comprimento = grupo.get('comprimentoM')?.value;
    const desenho = grupo.get('geometria')?.value;

    if (preenchido(area) || (preenchido(largura) && preenchido(comprimento)) || desenho != null) {
      return null;
    }
    return { tamanhoInformavel: true };
  };
}

function preenchido(valor: unknown): boolean {
  return valor !== null && valor !== undefined && valor !== '';
}

/** Preenche o formulario a partir de um imovel carregado do servidor. */
export function preencherForm(form: ImovelFormGroup, imovel: Imovel): void {
  form.setValue({
    proprietarioNome: imovel.proprietario.nome,
    municipio: imovel.municipio,
    uf: imovel.uf,
    bairro: imovel.bairro,
    rua: imovel.rua,
    numero: imovel.numero,
    latitude: imovel.latitude,
    longitude: imovel.longitude,
    areaM2: imovel.areaM2,
    larguraM: imovel.larguraM,
    comprimentoM: imovel.comprimentoM,
    // Só entra como desenho quando o lote NÃO veio de largura x comprimento:
    // um retângulo continua sendo editado pelas dimensões, que é mais preciso
    // do que arrastar quatro vértices.
    geometria: imovel.larguraM === null && imovel.geometria != null ? imovel.geometria : null,
    ativo: imovel.ativo,
  });
}

/** Converte o formulario no corpo que a API espera. */
export function formParaRequest(form: ImovelFormGroup): ImovelRequest {
  const valor = form.getRawValue();

  const geometria = valor.geometria ?? null;

  // Desenho e dimensões são excludentes: com o lote desenhado, largura e
  // comprimento não são enviados.
  const larguraM = geometria !== null ? null : numeroOuNulo(valor.larguraM);
  const comprimentoM = geometria !== null ? null : numeroOuNulo(valor.comprimentoM);

  const areaDerivada = geometria !== null || (larguraM !== null && comprimentoM !== null);

  return {
    proprietarioNome: valor.proprietarioNome.trim(),
    municipio: valor.municipio.trim(),
    uf: valor.uf.trim().toUpperCase(),
    bairro: valor.bairro.trim(),
    rua: valor.rua.trim(),
    numero: valor.numero.trim(),
    latitude: Number(valor.latitude),
    longitude: Number(valor.longitude),
    // Com dimensões ou com desenho, quem calcula a área é o servidor.
    areaM2: areaDerivada ? null : numeroOuNulo(valor.areaM2),
    larguraM,
    comprimentoM,
    geometria,
    ativo: valor.ativo,
  };
}

function numeroOuNulo(valor: number | null): number | null {
  if (valor === null || valor === undefined || Number.isNaN(Number(valor))) {
    return null;
  }
  return Number(valor);
}
