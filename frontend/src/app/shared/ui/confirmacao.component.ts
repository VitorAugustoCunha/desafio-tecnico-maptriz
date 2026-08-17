import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';

/**
 * Dialogo de confirmacao acessivel, no lugar do `confirm()` nativo.
 *
 * <p>O `confirm()` do navegador bloqueia a thread, nao e estilizavel, nao pode
 * ser testado e nao permite descrever o que sera excluido. Aqui o dialogo usa o
 * elemento `<dialog>` nativo, que ja entrega captura de foco, fechamento por
 * Esc e semantica de modal para leitor de tela — sem trazer biblioteca de UI
 * so para isso.
 */
@Component({
  selector: 'app-confirmacao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialogo class="dialogo" (close)="cancelar.emit()" (cancel)="cancelar.emit()">
      <h2 class="dialogo__titulo">{{ titulo() }}</h2>
      <p class="dialogo__mensagem">{{ mensagem() }}</p>

      <div class="dialogo__acoes">
        <button type="button" class="botao botao--neutro" (click)="cancelar.emit()">
          {{ rotuloCancelar() }}
        </button>
        <button type="button" [class]="'botao botao--' + tom()" (click)="confirmar.emit()">
          {{ rotuloConfirmar() }}
        </button>
      </div>
    </dialog>
  `,
  styles: `
    .dialogo {
      border: none;
      border-radius: 0.75rem;
      padding: 1.5rem;
      max-width: min(30rem, calc(100vw - 2rem));
      background: var(--superficie);
      color: var(--texto);
      box-shadow: 0 10px 30px rgb(0 0 0 / 25%);
    }

    .dialogo::backdrop {
      background: rgb(0 0 0 / 45%);
    }

    .dialogo__titulo {
      margin: 0 0 0.5rem;
      font-size: 1.125rem;
    }

    .dialogo__mensagem {
      margin: 0 0 1.5rem;
      color: var(--texto-suave);
      line-height: 1.5;
    }

    .dialogo__acoes {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
    }
  `,
})
export class ConfirmacaoComponent {
  readonly aberto = input.required<boolean>();
  readonly titulo = input('Confirmar');
  readonly mensagem = input('');
  readonly rotuloConfirmar = input('Confirmar');
  readonly rotuloCancelar = input('Cancelar');

  /**
   * Cor do botao de confirmar.
   *
   * <p>Padrao `perigo` porque o primeiro uso do dialogo foi a exclusao. Uma
   * confirmacao que apenas cria dado nao deve vir vermelha: o vermelho perde o
   * significado se aparecer em tudo.
   */
  readonly tom = input<'perigo' | 'primario'>('perigo');

  readonly confirmar = output<void>();
  readonly cancelar = output<void>();

  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  constructor() {
    effect(() => {
      const elemento = this.dialogo().nativeElement;

      if (this.aberto()) {
        if (!elemento.open) {
          // showModal (e nao show) e o que da captura de foco e fundo inerte.
          elemento.showModal();
        }
      } else if (elemento.open) {
        elemento.close();
      }
    });
  }
}
