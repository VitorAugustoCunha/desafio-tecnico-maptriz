import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';

import { AmostraApiService } from '../../../core/api/amostra-api.service';
import { ImovelApiService } from '../../../core/api/imovel-api.service';
import { traduzirErro } from '../../../core/api/erro-da-api';
import { Direcao, ImovelListItem, OrdemImovel } from '../../../core/models/imovel.model';
import { consultaParaQueryParams, queryParamsParaConsulta } from '../../../core/state/consulta-params';
import { ImoveisStore } from '../../../core/state/imoveis.store';
import { NotificacoesService } from '../../../core/ui/notificacoes.service';
import { ConfirmacaoComponent } from '../../../shared/ui/confirmacao.component';
import { PaginacaoComponent } from '../../../shared/ui/paginacao.component';

/** Espera antes de aplicar o filtro digitado, para nao consultar a cada tecla. */
const ESPERA_DO_FILTRO = 350;

/** Tamanho da carga de demonstracao. Confere com o padrao do servidor. */
const LOTES_DA_AMOSTRA = 1000;

@Component({
  selector: 'app-imoveis-lista',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, ReactiveFormsModule, RouterLink, PaginacaoComponent, ConfirmacaoComponent],
  templateUrl: './imoveis-lista.component.html',
  styleUrl: './imoveis-lista.component.scss',
})
export class ImoveisListaComponent {
  private readonly store = inject(ImoveisStore);
  private readonly api = inject(ImovelApiService);
  private readonly amostraApi = inject(AmostraApiService);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notificacoes = inject(NotificacoesService);

  /** A URL e a fonte da verdade da consulta: deep-link e F5 funcionam de graca. */
  private readonly parametros = toSignal(this.rota.queryParams, { initialValue: {} });

  readonly consulta = this.store.consulta;
  readonly itens = this.store.itens;
  readonly carregando = this.store.carregando;
  readonly erro = this.store.erro;
  readonly vazio = this.store.vazio;
  readonly total = this.store.totalDeElementos;
  readonly totalDePaginas = this.store.totalDePaginas;
  readonly primeira = this.store.primeira;
  readonly ultima = this.store.ultima;
  readonly areaTotal = this.store.areaTotalDaPagina;

  readonly filtros = new FormGroup({
    proprietarioNome: new FormControl('', { nonNullable: true }),
    municipio: new FormControl('', { nonNullable: true }),
  });

  private readonly _paraExcluir = signal<ImovelListItem | null>(null);
  readonly paraExcluir = this._paraExcluir.asReadonly();
  readonly excluindo = signal(false);

  readonly lotesDaAmostra = LOTES_DA_AMOSTRA;
  readonly confirmandoAmostra = signal(false);
  readonly gerandoAmostra = signal(false);

  readonly mensagemDaAmostra =
    `Serão criados ${LOTES_DA_AMOSTRA} imóveis de demonstração em Bauru/SP, ` +
    'todos com largura e comprimento definidos e polígono em EPSG:31982. ' +
    'Eles ficam no bairro "Distrito Amostra", que serve para localizá-los e removê-los depois.';

  readonly mensagemDeExclusao = computed(() => {
    const imovel = this._paraExcluir();
    return imovel === null
      ? ''
      : `O imóvel de ${imovel.proprietarioNome} em ${imovel.municipio} será excluído. Esta ação não pode ser desfeita.`;
  });

  readonly temFiltroAplicado = computed(() => {
    const consulta = this.consulta();
    return consulta.proprietarioNome !== '' || consulta.municipio !== '' || consulta.proprietarioId !== null;
  });

  constructor() {
    // Rota -> store. Se a consulta ja estiver em cache, nao ha requisicao:
    // e este caminho que atende o requisito 3 ao voltar da edicao.
    effect(() => {
      this.store.garantirCarregado(queryParamsParaConsulta(this.parametros()));
    });

    // Store -> formulario de filtros, para o campo refletir a URL na carga direta.
    effect(() => {
      const consulta = this.consulta();
      const atual = this.filtros.getRawValue();

      if (atual.proprietarioNome !== consulta.proprietarioNome || atual.municipio !== consulta.municipio) {
        this.filtros.setValue(
          { proprietarioNome: consulta.proprietarioNome, municipio: consulta.municipio },
          { emitEvent: false },
        );
      }
    });

    this.filtros.valueChanges
      .pipe(
        debounceTime(ESPERA_DO_FILTRO),
        distinctUntilChanged((a, b) => a.proprietarioNome === b.proprietarioNome && a.municipio === b.municipio),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.aplicarFiltros());
  }

  private aplicarFiltros(): void {
    const { proprietarioNome, municipio } = this.filtros.getRawValue();

    // Filtro novo sempre volta para a primeira pagina: continuar na pagina 7 de
    // um resultado que agora tem 2 paginas mostraria uma tela vazia.
    this.navegar({ ...this.consulta(), proprietarioNome, municipio, pagina: 0 });
  }

  irParaPagina(pagina: number): void {
    this.navegar({ ...this.consulta(), pagina });
  }

  trocarTamanho(tamanho: number): void {
    this.navegar({ ...this.consulta(), tamanho, pagina: 0 });
  }

  /** Clicar no cabecalho ordena; clicar de novo inverte a direcao. */
  ordenarPor(campo: OrdemImovel): void {
    const consulta = this.consulta();
    const direcao: Direcao = consulta.ordenarPor === campo && consulta.direcao === 'asc' ? 'desc' : 'asc';

    this.navegar({ ...consulta, ordenarPor: campo, direcao, pagina: 0 });
  }

  limparFiltros(): void {
    this.filtros.setValue({ proprietarioNome: '', municipio: '' }, { emitEvent: false });
    this.navegar({ ...this.consulta(), proprietarioNome: '', municipio: '', proprietarioId: null, pagina: 0 });
  }

  atualizar(): void {
    this.store.recarregar();
  }

  pedirExclusao(imovel: ImovelListItem): void {
    this._paraExcluir.set(imovel);
  }

  cancelarExclusao(): void {
    this._paraExcluir.set(null);
  }

  confirmarExclusao(): void {
    const imovel = this._paraExcluir();
    if (imovel === null || this.excluindo()) {
      return;
    }

    this.excluindo.set(true);

    this.api.excluir(imovel.id).subscribe({
      next: () => {
        this.excluindo.set(false);
        this._paraExcluir.set(null);
        this.notificacoes.sucesso(`Imóvel de ${imovel.proprietarioNome} excluído.`);

        // Excluir muda a composicao das paginas: aqui o cache precisa cair.
        this.store.invalidar();
        this.store.recarregar();
      },
      error: (erro: unknown) => {
        this.excluindo.set(false);
        this._paraExcluir.set(null);
        this.notificacoes.erro(traduzirErro(erro).mensagem);
      },
    });
  }

  pedirAmostra(): void {
    this.confirmandoAmostra.set(true);
  }

  cancelarAmostra(): void {
    this.confirmandoAmostra.set(false);
  }

  /**
   * Gera a massa de demonstracao.
   *
   * <p>O servidor pode devolver menos do que o pedido: lote que cairia sobre um
   * imovel existente e pulado, em vez de derrubar a carga inteira. A mensagem
   * diz o numero real, nao o pedido.
   */
  confirmarAmostra(): void {
    if (this.gerandoAmostra()) {
      return;
    }

    this.gerandoAmostra.set(true);
    this.confirmandoAmostra.set(false);

    this.amostraApi.gerarImoveis(LOTES_DA_AMOSTRA).subscribe({
      next: (amostra) => {
        this.gerandoAmostra.set(false);

        const detalhe =
          amostra.ignorados > 0
            ? ` (${amostra.ignorados} ignorado(s) por conflito de área)`
            : '';

        this.notificacoes.sucesso(
          `${amostra.criados} imóveis de amostra criados em ${amostra.municipio}${detalhe}.`,
        );

        // Criar em massa muda a composicao de todas as paginas.
        this.store.invalidar();
        this.store.recarregar();
      },
      error: (erro: unknown) => {
        this.gerandoAmostra.set(false);
        this.notificacoes.erro(traduzirErro(erro).mensagem);
      },
    });
  }

  /** Indicador de ordenacao para leitor de tela e para o CSS. */
  direcaoDaColuna(campo: OrdemImovel): 'ascending' | 'descending' | 'none' {
    const consulta = this.consulta();
    if (consulta.ordenarPor !== campo) {
      return 'none';
    }
    return consulta.direcao === 'asc' ? 'ascending' : 'descending';
  }

  paramsDaConsulta(): Record<string, unknown> {
    return consultaParaQueryParams(this.consulta());
  }

  private navegar(consulta: ReturnType<typeof this.consulta>): void {
    void this.router.navigate([], {
      relativeTo: this.rota,
      queryParams: consultaParaQueryParams(consulta),
      // Substitui em vez de empilhar: digitar um filtro nao deve encher o
      // historico de estados intermediarios.
      replaceUrl: true,
    });
  }
}
