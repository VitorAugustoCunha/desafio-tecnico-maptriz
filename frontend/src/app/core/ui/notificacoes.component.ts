import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { NotificacoesService } from './notificacoes.service';

@Component({
  selector: 'app-notificacoes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- aria-live: o leitor de tela anuncia a mensagem sem tirar o foco de onde o usuario esta. -->
    <div class="notificacoes" role="status" aria-live="polite" aria-atomic="false">
      @for (notificacao of notificacoes(); track notificacao.id) {
        <div class="notificacao" [class]="'notificacao--' + notificacao.tipo">
          <span class="notificacao__texto">{{ notificacao.mensagem }}</span>
          <button
            type="button"
            class="notificacao__fechar"
            [attr.aria-label]="'Fechar aviso: ' + notificacao.mensagem"
            (click)="fechar(notificacao.id)"
          >
            &times;
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .notificacoes {
      position: fixed;
      top: 1rem;
      right: 1rem;
      z-index: 100;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      max-width: min(28rem, calc(100vw - 2rem));
    }

    .notificacao {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.75rem 1rem;
      border-radius: 0.5rem;
      border-left: 4px solid;
      background: var(--superficie);
      box-shadow: 0 4px 12px rgb(0 0 0 / 15%);
      animation: entrar 150ms ease-out;
    }

    .notificacao--sucesso {
      border-color: var(--sucesso);
    }
    .notificacao--erro {
      border-color: var(--perigo);
    }
    .notificacao--aviso {
      border-color: var(--aviso);
    }

    .notificacao__texto {
      flex: 1;
      font-size: 0.9rem;
      line-height: 1.4;
    }

    .notificacao__fechar {
      background: none;
      border: none;
      cursor: pointer;
      font-size: 1.25rem;
      line-height: 1;
      padding: 0 0.25rem;
      color: var(--texto-suave);
    }

    .notificacao__fechar:hover {
      color: var(--texto);
    }

    @keyframes entrar {
      from {
        opacity: 0;
        transform: translateX(1rem);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .notificacao {
        animation: none;
      }
    }
  `,
})
export class NotificacoesComponent {
  private readonly servico = inject(NotificacoesService);

  readonly notificacoes = this.servico.notificacoes;

  fechar(id: number): void {
    this.servico.fechar(id);
  }
}
