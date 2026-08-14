import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ProprietarioApiService } from '../../../core/api/proprietario-api.service';
import { traduzirErro } from '../../../core/api/erro-da-api';
import { PAGINA_VAZIA, Pagina } from '../../../core/models/pagina.model';
import { ErroDaApi } from '../../../core/models/problema.model';
import { ProprietarioListItem } from '../../../core/models/proprietario.model';
import { PaginacaoComponent } from '../../../shared/ui/paginacao.component';

const ESPERA_DA_BUSCA = 350;

@Component({
  selector: 'app-proprietarios-lista',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, PaginacaoComponent],
  templateUrl: './proprietarios-lista.component.html',
  styleUrl: './proprietarios-lista.component.scss',
})
export class ProprietariosListaComponent {
  private readonly api = inject(ProprietarioApiService);

  readonly busca = new FormControl('', { nonNullable: true });

  readonly pagina = signal<Pagina<ProprietarioListItem>>(PAGINA_VAZIA);
  readonly carregando = signal(false);
  readonly erro = signal<ErroDaApi | null>(null);

  private paginaAtual = 0;
  private tamanho = 20;

  constructor() {
    this.carregar();

    this.busca.valueChanges
      .pipe(debounceTime(ESPERA_DA_BUSCA), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.paginaAtual = 0;
        this.carregar();
      });
  }

  irParaPagina(pagina: number): void {
    this.paginaAtual = pagina;
    this.carregar();
  }

  trocarTamanho(tamanho: number): void {
    this.tamanho = tamanho;
    this.paginaAtual = 0;
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.api.listar(this.busca.value, this.paginaAtual, this.tamanho).subscribe({
      next: (pagina) => {
        this.pagina.set(pagina);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.erro.set(traduzirErro(erro));
        this.carregando.set(false);
      },
    });
  }

  get temBusca(): boolean {
    return this.busca.value.trim().length > 0;
  }
}
