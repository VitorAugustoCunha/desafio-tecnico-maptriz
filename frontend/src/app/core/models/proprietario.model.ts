export interface ProprietarioListItem {
  readonly id: number;
  readonly nome: string;
  readonly quantidadeImoveis: number;
}

export interface Proprietario {
  readonly id: number;
  readonly nome: string;
  readonly quantidadeImoveis: number;
  readonly criadoEm: string;
  readonly atualizadoEm: string;
}

export interface ProprietarioRequest {
  readonly nome: string;
}
