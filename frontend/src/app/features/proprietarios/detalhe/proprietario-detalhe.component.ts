import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ImovelApiService } from '../../../core/api/imovel-api.service';
import { ProprietarioApiService } from '../../../core/api/proprietario-api.service';
import { traduzirErro } from '../../../core/api/erro-da-api';
import { CONSULTA_PADRAO, ImovelListItem } from '../../../core/models/imovel.model';
import { PAGINA_VAZIA, Pagina } from '../../../core/models/pagina.model';
import { ErroDaApi } from '../../../core/models/problema.model';
import { Proprietario } from '../../../core/models/proprietario.model';
import { NotificacoesService } from '../../../core/ui/notificacoes.service';
import { PaginacaoComponent } from '../../../shared/ui/paginacao.component';

/**
 * Detalhe do proprietario com os imoveis dele (tarefas 4 e 5).
 *
 * <p>Os imoveis vem da listagem paginada filtrada por `proprietarioId`, e nao
 * embutidos na resposta do proprietario: um titular com milhares de imoveis
 * traria de volta o problema de volume que a tarefa 6 resolve.
 */
@Component({
  selector: 'app-proprietario-detalhe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, ReactiveFormsModule, RouterLink, PaginacaoComponent],
  templateUrl: './proprietario-detalhe.component.html',
  styleUrl: './proprietario-detalhe.component.scss',
})
export class ProprietarioDetalheComponent {
  private readonly apiProprietarios = inject(ProprietarioApiService);
  private readonly apiImoveis = inject(ImovelApiService);
  private readonly rota = inject(ActivatedRoute);
  private readonly notificacoes = inject(NotificacoesService);

  private readonly id = Number(this.rota.snapshot.paramMap.get('id'));

  readonly proprietario = signal<Proprietario | null>(null);
  readonly imoveis = signal<Pagina<ImovelListItem>>(PAGINA_VAZIA);

  readonly carregando = signal(true);
  readonly carregandoImoveis = signal(false);
  readonly erro = signal<ErroDaApi | null>(null);

  readonly editandoNome = signal(false);
  readonly salvandoNome = signal(false);
  readonly nome = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(120)],
  });

  private paginaAtual = 0;
  private tamanho = 20;

  constructor() {
    this.carregar();
    this.carregarImoveis();
  }

  private carregar(): void {
    this.carregando.set(true);

    this.apiProprietarios.buscar(this.id).subscribe({
      next: (proprietario) => {
        this.proprietario.set(proprietario);
        this.nome.setValue(proprietario.nome);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.erro.set(traduzirErro(erro));
        this.carregando.set(false);
      },
    });
  }

  carregarImoveis(): void {
    this.carregandoImoveis.set(true);

    this.apiImoveis
      .listar({
        ...CONSULTA_PADRAO,
        proprietarioId: this.id,
        pagina: this.paginaAtual,
        tamanho: this.tamanho,
      })
      .subscribe({
        next: (pagina) => {
          this.imoveis.set(pagina);
          this.carregandoImoveis.set(false);
        },
        error: (erro: unknown) => {
          this.notificacoes.erro(traduzirErro(erro).mensagem);
          this.carregandoImoveis.set(false);
        },
      });
  }

  irParaPagina(pagina: number): void {
    this.paginaAtual = pagina;
    this.carregarImoveis();
  }

  trocarTamanho(tamanho: number): void {
    this.tamanho = tamanho;
    this.paginaAtual = 0;
    this.carregarImoveis();
  }

  comecarEdicao(): void {
    this.nome.setValue(this.proprietario()?.nome ?? '');
    this.editandoNome.set(true);
  }

  cancelarEdicao(): void {
    this.editandoNome.set(false);
    this.nome.setErrors(null);
  }

  /**
   * Renomeia o titular.
   *
   * <p>Uma chamada, uma entidade alterada. Os imoveis exibidos abaixo sao
   * recarregados para mostrar que a mudanca valeu para todos eles — que e
   * exatamente o que o requisito 5 pede para demonstrar.
   */
  salvarNome(): void {
    if (this.salvandoNome() || this.nome.invalid) {
      this.nome.markAsTouched();
      return;
    }

    this.salvandoNome.set(true);

    this.apiProprietarios.renomear(this.id, this.nome.value.trim()).subscribe({
      next: (atualizado) => {
        this.proprietario.set(atualizado);
        this.salvandoNome.set(false);
        this.editandoNome.set(false);
        this.notificacoes.sucesso(
          `Nome atualizado em ${atualizado.quantidadeImoveis} imóvel(is).`,
        );
        this.carregarImoveis();
      },
      error: (erro: unknown) => {
        const traduzido = traduzirErro(erro);
        this.salvandoNome.set(false);
        this.nome.setErrors({ servidor: traduzido.mensagem });
        this.notificacoes.erro(traduzido.mensagem);
      },
    });
  }

  get erroDoNome(): string | null {
    if (!this.nome.touched && !this.nome.errors?.['servidor']) {
      return null;
    }
    const erros = this.nome.errors ?? {};
    if (typeof erros['servidor'] === 'string') {
      return erros['servidor'];
    }
    if (erros['required']) {
      return 'Informe o nome.';
    }
    if (erros['maxlength']) {
      return 'No máximo 120 caracteres.';
    }
    return null;
  }
}
