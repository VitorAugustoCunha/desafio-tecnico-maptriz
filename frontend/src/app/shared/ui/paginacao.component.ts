import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** Controles de paginacao. Puramente apresentacional: recebe estado, emite intencao. */
@Component({
  selector: 'app-paginacao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="paginacao" [attr.aria-label]="'Paginacao'">
      <p class="paginacao__resumo" aria-live="polite">
        @if (total() > 0) {
          Mostrando {{ primeiroItem() }}–{{ ultimoItem() }} de {{ total() }}
        } @else {
          Nenhum resultado
        }
      </p>

      <div class="paginacao__controles">
        <button
          type="button"
          class="botao botao--neutro"
          [disabled]="primeira()"
          (click)="irPara.emit(pagina() - 1)"
        >
          Anterior
        </button>

        <span class="paginacao__posicao">
          Página {{ pagina() + 1 }} de {{ totalDePaginas() || 1 }}
        </span>

        <button
          type="button"
          class="botao botao--neutro"
          [disabled]="ultima()"
          (click)="irPara.emit(pagina() + 1)"
        >
          Próxima
        </button>
      </div>

      <label class="paginacao__tamanho">
        <span>Por página</span>
        <select [value]="tamanho()" (change)="aoTrocarTamanho($event)">
          @for (opcao of opcoesDeTamanho; track opcao) {
            <option [value]="opcao">{{ opcao }}</option>
          }
        </select>
      </label>
    </nav>
  `,
  styles: `
    .paginacao {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding-top: 1rem;
      border-top: 1px solid var(--borda);
    }

    .paginacao__resumo {
      margin: 0;
      color: var(--texto-suave);
      font-size: 0.875rem;
    }

    .paginacao__controles {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .paginacao__posicao {
      font-size: 0.875rem;
      white-space: nowrap;
    }

    .paginacao__tamanho {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      color: var(--texto-suave);
    }
  `,
})
export class PaginacaoComponent {
  readonly pagina = input.required<number>();
  readonly tamanho = input.required<number>();
  readonly total = input.required<number>();
  readonly totalDePaginas = input.required<number>();
  readonly primeira = input.required<boolean>();
  readonly ultima = input.required<boolean>();

  readonly irPara = output<number>();
  readonly trocarTamanho = output<number>();

  readonly opcoesDeTamanho = [10, 20, 50, 100] as const;

  readonly primeiroItem = computed(() => this.pagina() * this.tamanho() + 1);
  readonly ultimoItem = computed(() => Math.min((this.pagina() + 1) * this.tamanho(), this.total()));

  aoTrocarTamanho(evento: Event): void {
    const valor = Number((evento.target as HTMLSelectElement).value);
    this.trocarTamanho.emit(valor);
  }
}
