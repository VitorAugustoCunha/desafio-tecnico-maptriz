import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ImovelApiService } from '../../../core/api/imovel-api.service';
import { traduzirErro } from '../../../core/api/erro-da-api';
import { Imovel } from '../../../core/models/imovel.model';
import { ErroDaApi } from '../../../core/models/problema.model';
import { queryParamsParaConsulta } from '../../../core/state/consulta-params';
import { ImoveisStore } from '../../../core/state/imoveis.store';
import { NotificacoesService } from '../../../core/ui/notificacoes.service';
import { criarImovelForm, formParaRequest, preencherForm } from './imovel-form.model';

/**
 * Formulario de imovel, usado por `/imoveis/novo` e por `/imoveis/:id/editar`.
 *
 * <p>As duas telas compartilham campos, validacao e tratamento de erro, e so
 * diferem no carregamento inicial e no verbo HTTP — por isso um componente so,
 * em vez de dois quase iguais.
 */
@Component({
  selector: 'app-imovel-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './imovel-form.component.html',
  styleUrl: './imovel-form.component.scss',
})
export class ImovelFormComponent {
  private readonly api = inject(ImovelApiService);
  private readonly store = inject(ImoveisStore);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly notificacoes = inject(NotificacoesService);

  /**
   * O modo vem da presenca do parametro `:id` na rota.
   *
   * <p>Derivar de `data.modo` exigiria que o roteador estivesse configurado com
   * `withComponentInputBinding`, e criaria duas fontes de verdade que podem
   * divergir. A rota `/imoveis/:id/editar` tem id; `/imoveis/novo` nao tem.
   */
  private readonly idDaRota = this.rota.snapshot.paramMap.get('id');

  readonly editando = this.idDaRota !== null;
  readonly titulo = this.editando ? 'Editar imóvel' : 'Novo imóvel';

  readonly form = criarImovelForm();

  readonly carregando = signal(false);
  readonly salvando = signal(false);
  readonly erroDeCarga = signal<ErroDaApi | null>(null);
  readonly erroDoServidor = signal<ErroDaApi | null>(null);

  /** `true` quando as duas dimensoes estao preenchidas: a area passa a ser derivada. */
  readonly areaDerivada = signal(false);

  private imovelCarregado: Imovel | null = null;

  constructor() {
    if (this.idDaRota !== null) {
      this.carregar(Number(this.idDaRota));
    }

    this.form.valueChanges.subscribe((valor) => {
      const derivada = preenchido(valor.larguraM) && preenchido(valor.comprimentoM);
      this.areaDerivada.set(derivada);

      if (derivada && this.form.controls.areaM2.enabled) {
        this.form.controls.areaM2.disable({ emitEvent: false });
      } else if (!derivada && this.form.controls.areaM2.disabled) {
        this.form.controls.areaM2.enable({ emitEvent: false });
      }
    });
  }

  private carregar(id: number): void {
    this.carregando.set(true);

    this.api.buscar(id).subscribe({
      next: (imovel) => {
        this.imovelCarregado = imovel;
        // Copia imutavel: o formulario nunca escreve no objeto que a lista usa.
        // No codigo original, `this.form = i` fazia a tabela mudar enquanto se
        // digitava, e continuar alterada mesmo ao cancelar.
        preencherForm(this.form, imovel);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.erroDeCarga.set(traduzirErro(erro));
        this.carregando.set(false);
      },
    });
  }

  salvar(): void {
    // Evita envio duplicado por clique repetido ou Enter apertado duas vezes.
    if (this.salvando()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notificacoes.aviso('Revise os campos destacados antes de salvar.');
      return;
    }

    this.salvando.set(true);
    this.erroDoServidor.set(null);

    const corpo = formParaRequest(this.form);

    if (this.editando && this.imovelCarregado !== null) {
      this.api.atualizar(this.imovelCarregado.id, corpo).subscribe({
        next: (imovel) => {
          // Corrige o item no cache e volta: a listagem reaproveita o estado
          // que ja tinha, sem nova requisicao (requisito 3).
          this.store.aplicarImovelAtualizado(imovel);
          this.salvando.set(false);
          this.notificacoes.sucesso('Imóvel atualizado.');
          this.voltarParaListagem();
        },
        error: (erro: unknown) => this.tratarErro(erro),
      });
      return;
    }

    this.api.criar(corpo).subscribe({
      next: (imovel) => {
        // Criar muda a composicao das paginas; aqui o cache precisa cair.
        this.store.invalidar();
        this.salvando.set(false);
        this.notificacoes.sucesso(`Imóvel de ${imovel.proprietario.nome} cadastrado.`);
        this.voltarParaListagem();
      },
      error: (erro: unknown) => this.tratarErro(erro),
    });
  }

  cancelar(): void {
    this.voltarParaListagem();
  }

  /**
   * Volta preservando os parametros da consulta.
   *
   * <p>Preservar importa para o requisito 3: a chave de cache inclui filtros,
   * pagina e ordenacao. Voltar sem eles seria outra consulta, e a listagem
   * dispararia um GET novo.
   */
  private voltarParaListagem(): void {
    const consulta = queryParamsParaConsulta(this.rota.snapshot.queryParams);

    void this.router.navigate(['/imoveis'], {
      queryParams: this.rota.snapshot.queryParams,
      // Mantem a consulta corrente do store alinhada com a rota de destino.
      state: { origem: 'formulario', consulta },
    });
  }

  private tratarErro(erro: unknown): void {
    const traduzido = traduzirErro(erro);

    this.salvando.set(false);
    this.erroDoServidor.set(traduzido);

    // Erro por campo vindo do servidor marca o input correspondente.
    for (const [campo, mensagem] of traduzido.errosPorCampo) {
      const controle = this.form.get(campo);
      if (controle !== null) {
        controle.setErrors({ ...(controle.errors ?? {}), servidor: mensagem });
        controle.markAsTouched();
      }
    }

    this.notificacoes.erro(traduzido.mensagem);
  }

  /** Mensagem de erro de um campo, na ordem de prioridade que faz sentido para quem preenche. */
  erroDoCampo(nome: string): string | null {
    const controle = this.form.get(nome);

    if (controle === null || !controle.touched || controle.valid) {
      return null;
    }

    const erros = controle.errors ?? {};

    if (typeof erros['servidor'] === 'string') {
      return erros['servidor'];
    }
    if (erros['required']) {
      return 'Campo obrigatório.';
    }
    if (erros['pattern']) {
      return 'Use a sigla de 2 letras, por exemplo SP.';
    }
    if (erros['maxlength']) {
      const limite = erros['maxlength'] as { requiredLength: number };
      return `No máximo ${limite.requiredLength} caracteres.`;
    }
    if (erros['min'] || erros['max']) {
      return nome === 'latitude' ? 'A latitude deve estar entre -90 e 90.' : 'A longitude deve estar entre -180 e 180.';
    }
    if (erros['maiorQueZero']) {
      return 'Informe um valor maior que zero.';
    }

    return 'Valor inválido.';
  }

  get erroDeTamanho(): string | null {
    if (!this.form.touched) {
      return null;
    }
    if (this.form.errors?.['dimensoesEmPar']) {
      return 'Informe largura e comprimento juntos, ou nenhum dos dois.';
    }
    if (this.form.errors?.['tamanhoInformavel']) {
      return 'Informe a área, ou largura e comprimento.';
    }
    return null;
  }
}

function preenchido(valor: unknown): boolean {
  return valor !== null && valor !== undefined && valor !== '';
}
