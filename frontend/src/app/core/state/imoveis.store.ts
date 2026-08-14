import { Injectable, computed, inject, signal } from '@angular/core';

import { ImovelApiService } from '../api/imovel-api.service';
import { traduzirErro } from '../api/erro-da-api';
import { APP_CONFIG } from '../config/app-config';
import {
  CONSULTA_PADRAO,
  ConsultaImoveis,
  Imovel,
  ImovelListItem,
  chaveDaConsulta,
} from '../models/imovel.model';
import { Pagina } from '../models/pagina.model';
import { ErroDaApi } from '../models/problema.model';

/**
 * Estado em memoria da listagem de imoveis.
 *
 * <p><b>Requisito 3 do desafio:</b> ao voltar da edicao para a listagem nao pode
 * haver nova requisicao. Este store e o que garante isso.
 *
 * <h3>Politica de invalidacao</h3>
 *
 * O cache guarda <b>uma</b> consulta por vez, identificada por
 * {@link chaveDaConsulta} (filtros + pagina + tamanho + ordenacao). A regra e:
 *
 * <ul>
 *   <li><b>reaproveita</b> quando a chave e a mesma e o dado ainda esta dentro
 *       da validade — e o caso de voltar da edicao, que leva segundos;</li>
 *   <li><b>busca de novo</b> quando qualquer parametro muda (filtro, pagina,
 *       ordenacao): sao dados diferentes, nao ha o que reaproveitar;</li>
 *   <li><b>busca de novo</b> quando o usuario entra pela URL ou atualiza o
 *       navegador: o store vive em memoria e nasce vazio;</li>
 *   <li><b>busca de novo</b> depois de criar ou excluir, porque isso muda a
 *       composicao e a contagem das paginas — corrigir isso no cliente daria
 *       uma tela que discorda do servidor;</li>
 *   <li><b>corrige em memoria</b> depois de editar: o {@code PUT} devolve o
 *       imovel atualizado e {@link #aplicarImovelAtualizado} troca aquele item
 *       de forma imutavel, sem ida ao servidor;</li>
 *   <li><b>busca de novo</b> depois da validade ({@code tempoDeCacheDaListagem}),
 *       para uma aba esquecida aberta nao exibir dado de uma hora atras.</li>
 * </ul>
 *
 * <p>Detalhe assumido: se a edicao tirar o imovel do filtro corrente (mudar o
 * municipio, por exemplo), a linha <b>continua visivel</b> com os dados novos
 * ate a proxima consulta real. Preferi isso a fazer a linha desaparecer no
 * instante em que o usuario salva — sumir sem explicacao parece perda de dado.
 */
@Injectable({ providedIn: 'root' })
export class ImoveisStore {
  private readonly api = inject(ImovelApiService);
  private readonly config = inject(APP_CONFIG);

  private readonly _consulta = signal<ConsultaImoveis>(CONSULTA_PADRAO);
  private readonly _chaveEmCache = signal<string | null>(null);
  private readonly _pagina = signal<Pagina<ImovelListItem> | null>(null);
  private readonly _carregando = signal(false);
  private readonly _erro = signal<ErroDaApi | null>(null);
  private readonly _carregadoEm = signal(0);

  /** Consulta que a tela esta exibindo. */
  readonly consulta = this._consulta.asReadonly();
  readonly carregando = this._carregando.asReadonly();
  readonly erro = this._erro.asReadonly();

  readonly itens = computed<readonly ImovelListItem[]>(() => this._pagina()?.conteudo ?? []);
  readonly totalDeElementos = computed(() => this._pagina()?.totalDeElementos ?? 0);
  readonly totalDePaginas = computed(() => this._pagina()?.totalDePaginas ?? 0);
  readonly primeira = computed(() => this._pagina()?.primeira ?? true);
  readonly ultima = computed(() => this._pagina()?.ultima ?? true);

  /** `true` quando a consulta terminou sem nenhum resultado (estado vazio != erro). */
  readonly vazio = computed(
    () => !this._carregando() && this._erro() === null && this._pagina() !== null && this.itens().length === 0,
  );

  /**
   * Area total da pagina exibida.
   *
   * <p>No codigo original isso era uma funcao chamada direto do template, entao
   * o laco rodava a cada ciclo de deteccao de mudancas. Como `computed`, so
   * recalcula quando a lista muda de fato.
   */
  readonly areaTotalDaPagina = computed(() =>
    this.itens().reduce((soma, imovel) => soma + (imovel.areaM2 ?? 0), 0),
  );

  /**
   * Garante que a consulta pedida esteja carregada.
   *
   * <p>Quando ja esta em cache e valida, <b>nao emite requisicao nenhuma</b>.
   */
  garantirCarregado(consulta: ConsultaImoveis): void {
    const chave = chaveDaConsulta(consulta);

    if (this.temEmCache(chave)) {
      // Mantem a consulta corrente sincronizada com a rota sem tocar na rede.
      this._consulta.set(consulta);
      return;
    }

    this.buscar(consulta);
  }

  /** Forca uma nova busca, mesmo com cache valido. Usado pelo botao "Atualizar". */
  recarregar(): void {
    this.buscar(this._consulta());
  }

  /**
   * Aplica o imovel devolvido pelo `PUT` sobre o item correspondente do cache.
   *
   * <p>Substituicao imutavel: cria um novo array e um novo item, sem alterar os
   * objetos existentes. Era exatamente o oposto do codigo original, onde
   * `this.form = i` fazia o formulario editar a linha da tabela por referencia —
   * a tabela mudava antes de salvar, e continuava alterada mesmo ao cancelar.
   */
  aplicarImovelAtualizado(imovel: Imovel): void {
    const pagina = this._pagina();
    if (pagina === null) {
      return;
    }

    let encontrou = false;

    const conteudo = pagina.conteudo.map((item) => {
      if (item.id !== imovel.id) {
        return item;
      }
      encontrou = true;
      return paraListItem(imovel);
    });

    if (!encontrou) {
      return;
    }

    this._pagina.set({ ...pagina, conteudo });
  }

  /**
   * Descarta o cache.
   *
   * <p>Chamado depois de criar e de excluir: as duas operacoes mudam quais
   * imoveis caem em qual pagina, e nenhuma correcao local reproduziria isso com
   * fidelidade.
   */
  invalidar(): void {
    this._chaveEmCache.set(null);
  }

  /** Estado atual do cache, para diagnostico e para os testes. */
  emCache(consulta: ConsultaImoveis): boolean {
    return this.temEmCache(chaveDaConsulta(consulta));
  }

  private temEmCache(chave: string): boolean {
    return (
      this._chaveEmCache() === chave &&
      this._pagina() !== null &&
      Date.now() - this._carregadoEm() < this.config.tempoDeCacheDaListagem
    );
  }

  private buscar(consulta: ConsultaImoveis): void {
    this._consulta.set(consulta);
    this._carregando.set(true);
    this._erro.set(null);

    this.api.listar(consulta).subscribe({
      next: (pagina) => {
        this._pagina.set(pagina);
        this._chaveEmCache.set(chaveDaConsulta(consulta));
        this._carregadoEm.set(Date.now());
        this._carregando.set(false);
      },
      error: (erro: unknown) => {
        this._erro.set(traduzirErro(erro));
        this._carregando.set(false);
        // Cache invalidado: um erro nao pode deixar dado velho passando por atual.
        this._chaveEmCache.set(null);
      },
    });
  }
}

function paraListItem(imovel: Imovel): ImovelListItem {
  return {
    id: imovel.id,
    proprietarioId: imovel.proprietario.id,
    proprietarioNome: imovel.proprietario.nome,
    municipio: imovel.municipio,
    uf: imovel.uf,
    bairro: imovel.bairro,
    rua: imovel.rua,
    numero: imovel.numero,
    latitude: imovel.latitude,
    longitude: imovel.longitude,
    areaM2: imovel.areaM2,
    ativo: imovel.ativo,
  };
}
