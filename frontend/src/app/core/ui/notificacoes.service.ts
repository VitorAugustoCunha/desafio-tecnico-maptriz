import { Injectable, signal } from '@angular/core';

export type TipoDeNotificacao = 'sucesso' | 'erro' | 'aviso';

export interface Notificacao {
  readonly id: number;
  readonly tipo: TipoDeNotificacao;
  readonly mensagem: string;
}

/** Tempo que uma notificacao de sucesso fica na tela. */
const DURACAO_MS = 5000;

/**
 * Feedback ao usuario.
 *
 * <p>Substitui o `alert()` e o `console.log` do codigo original: `alert` trava a
 * thread, nao e estilizavel e nao da para testar, e log de console nao e
 * feedback — o usuario nunca ve.
 *
 * <p>As mensagens sao anunciadas por leitor de tela atraves de uma regiao
 * `aria-live` no componente que as renderiza.
 */
@Injectable({ providedIn: 'root' })
export class NotificacoesService {
  private readonly _notificacoes = signal<readonly Notificacao[]>([]);
  readonly notificacoes = this._notificacoes.asReadonly();

  private proximoId = 1;
  private readonly temporizadores = new Map<number, ReturnType<typeof setTimeout>>();

  sucesso(mensagem: string): void {
    this.publicar('sucesso', mensagem, true);
  }

  aviso(mensagem: string): void {
    this.publicar('aviso', mensagem, true);
  }

  /** Erro nao some sozinho: quem precisa ler a mensagem decide quando fechar. */
  erro(mensagem: string): void {
    this.publicar('erro', mensagem, false);
  }

  fechar(id: number): void {
    this.cancelarTemporizador(id);
    this._notificacoes.update((atuais) => atuais.filter((n) => n.id !== id));
  }

  limpar(): void {
    this.temporizadores.forEach((temporizador) => clearTimeout(temporizador));
    this.temporizadores.clear();
    this._notificacoes.set([]);
  }

  private publicar(tipo: TipoDeNotificacao, mensagem: string, autoFechar: boolean): void {
    const id = this.proximoId++;

    this._notificacoes.update((atuais) => [...atuais, { id, tipo, mensagem }]);

    if (autoFechar) {
      this.temporizadores.set(
        id,
        setTimeout(() => this.fechar(id), DURACAO_MS),
      );
    }
  }

  private cancelarTemporizador(id: number): void {
    const temporizador = this.temporizadores.get(id);
    if (temporizador !== undefined) {
      clearTimeout(temporizador);
      this.temporizadores.delete(id);
    }
  }
}
