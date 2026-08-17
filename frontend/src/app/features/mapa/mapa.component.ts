import { DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnDestroy,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import Feature from 'ol/Feature';
import Map from 'ol/Map';
import View from 'ol/View';
import { Attribution, defaults as controlesPadrao } from 'ol/control';
import { Point, Polygon } from 'ol/geom';
import { Vector as CamadaVetorial } from 'ol/layer';
import { fromLonLat, toLonLat } from 'ol/proj';
import { Vector as FonteVetorial } from 'ol/source';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { MapBrowserEvent } from 'ol';

import { MapaApiService, Viewport } from '../../core/api/mapa-api.service';
import { traduzirErro } from '../../core/api/erro-da-api';
import { ErroDaApi } from '../../core/models/problema.model';
import { camadaDeRuas } from './gis/camadas-base';
import { EstatisticasDoLote, PropriedadesDaFeicao } from './gis/gis-protocolo';
import { GisWorkerClient, ehPedidoObsoleto } from './gis/gis-worker.client';

/** Espera antes de consultar depois de mover o mapa. */
const ESPERA_DO_VIEWPORT = 300;

/** Centro inicial: Brasil inteiro visivel. */
const CENTRO_INICIAL: [number, number] = [-51.9, -14.2];
const ZOOM_INICIAL = 4;

interface Selecionado {
  readonly propriedades: PropriedadesDaFeicao;
}

@Component({
  selector: 'app-mapa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, RouterLink],
  templateUrl: './mapa.component.html',
  styleUrl: './mapa.component.scss',
})
export class MapaComponent implements AfterViewInit, OnDestroy {
  private readonly api = inject(MapaApiService);
  private readonly worker = inject(GisWorkerClient);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly elementoDoMapa = viewChild.required<ElementRef<HTMLDivElement>>('mapa');

  private mapa: Map | null = null;
  private observador: ResizeObserver | null = null;
  private readonly fonte = new FonteVetorial();

  /** Emite a cada movimento do mapa; o debounce e o switchMap resolvem a corrida. */
  private readonly movimentos = new Subject<{ viewport: Viewport; zoom: number }>();

  readonly carregando = signal(false);
  readonly erro = signal<ErroDaApi | null>(null);
  readonly estatisticas = signal<EstatisticasDoLote | null>(null);
  readonly descartadas = signal(0);
  readonly truncado = signal(false);
  readonly agregado = signal(false);
  readonly selecionado = signal<Selecionado | null>(null);
  readonly duracaoDoPipelineMs = signal(0);

  readonly usandoWorker = computed(() => this.worker.usandoWorker);

  readonly vazio = computed(
    () => !this.carregando() && this.erro() === null && (this.estatisticas()?.totalDeFeicoes ?? 0) === 0,
  );

  constructor() {
    this.movimentos
      .pipe(
        debounceTime(ESPERA_DO_VIEWPORT),
        // switchMap cancela a requisicao anterior: sem isso, arrastar o mapa
        // depressa deixa respostas antigas chegando depois das novas e a tela
        // termina exibindo o viewport errado.
        switchMap(({ viewport, zoom }) => {
          this.carregando.set(true);
          this.erro.set(null);
          return this.api.noViewport(viewport, zoom);
        }),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (colecao) => {
          this.agregado.set(colecao.metadados?.agregado ?? false);
          this.truncado.set(colecao.metadados?.truncado ?? false);
          void this.processar(colecao);
        },
        error: (erro: unknown) => {
          this.erro.set(traduzirErro(erro));
          this.carregando.set(false);
        },
      });
  }

  ngAfterViewInit(): void {
    this.montarMapa();
  }

  ngOnDestroy(): void {
    this.observador?.disconnect();
    this.observador = null;
    this.mapa?.setTarget(undefined);
    this.mapa = null;
  }

  private montarMapa(): void {
    const camadaDeFeicoes = new CamadaVetorial({
      source: this.fonte,
      style: (feicao) => this.estiloDa(feicao as Feature),
    });

    const container = this.elementoDoMapa().nativeElement;

    this.mapa = new Map({
      target: container,
      layers: [
        // Ruas por padrao aqui: nesta tela o objetivo e localizar imoveis, nao
        // digitalizar contorno. O editor do formulario usa satelite.
        camadaDeRuas(),
        camadaDeFeicoes,
      ],
      view: new View({
        center: fromLonLat(CENTRO_INICIAL),
        zoom: ZOOM_INICIAL,
        maxZoom: 19,
      }),
      controls: controlesPadrao({ attribution: false }).extend([
        new Attribution({ collapsible: false }),
      ]),
    });

    // Mesmo motivo do editor: o container pode ter altura 0 quando o mapa e
    // criado, e o OpenLayers mede o elemento nesse instante.
    this.observador = new ResizeObserver(() => {
      this.mapa?.updateSize();
      this.mapa?.renderSync();
    });
    this.observador.observe(container);

    setTimeout(() => {
      this.mapa?.updateSize();
      this.mapa?.renderSync();
    });

    this.mapa.on('moveend', () => this.aoMoverMapa());
    this.mapa.on('click', (evento) => this.aoClicar(evento));

    // Primeira carga.
    this.aoMoverMapa();

    this.destroyRef.onDestroy(() => this.movimentos.complete());
  }

  private aoMoverMapa(): void {
    const mapa = this.mapa;
    if (mapa === null) {
      return;
    }

    const view = mapa.getView();
    const extensao = view.calculateExtent(mapa.getSize());

    const [minLon, minLat] = toLonLat([extensao[0], extensao[1]]);
    const [maxLon, maxLat] = toLonLat([extensao[2], extensao[3]]);

    this.movimentos.next({
      viewport: {
        // Girar o globo produz longitude fora da faixa; o backend tambem recorta,
        // mas mandar valor valido evita um 400 desnecessario.
        minLon: Math.max(-180, minLon),
        minLat: Math.max(-90, minLat),
        maxLon: Math.min(180, maxLon),
        maxLat: Math.min(90, maxLat),
      },
      zoom: view.getZoom() ?? ZOOM_INICIAL,
    });
  }

  /** Manda o lote para o worker e desenha o que voltar. */
  private async processar(colecao: Parameters<GisWorkerClient['preparar']>[0]): Promise<void> {
    const inicio = performance.now();

    try {
      const resultado = await this.worker.preparar(colecao);

      this.duracaoDoPipelineMs.set(Math.round(performance.now() - inicio));
      this.estatisticas.set(resultado.estatisticas);
      this.descartadas.set(resultado.descartadas);

      this.desenhar(resultado.pontos, resultado.poligonos);
      this.carregando.set(false);
    } catch (erro) {
      // Pedido superado por outro mais recente nao e falha: o resultado que
      // interessa e o do pedido novo, que ainda esta a caminho.
      if (ehPedidoObsoleto(erro)) {
        return;
      }

      this.erro.set(traduzirErro(erro));
      this.carregando.set(false);
    }
  }

  private desenhar(
    pontos: readonly { lon: number; lat: number; propriedades: PropriedadesDaFeicao; rotulo: string }[],
    poligonos: readonly { anel: readonly (readonly [number, number])[]; propriedades: PropriedadesDaFeicao }[],
  ): void {
    this.fonte.clear();

    const feicoes: Feature[] = [];

    for (const poligono of poligonos) {
      const anel = poligono.anel.map(([lon, lat]) => fromLonLat([lon, lat]));
      const feicao = new Feature(new Polygon([anel]));
      feicao.setProperties({ dados: poligono.propriedades, ehPoligono: true });
      feicoes.push(feicao);
    }

    for (const ponto of pontos) {
      const feicao = new Feature(new Point(fromLonLat([ponto.lon, ponto.lat])));
      feicao.setProperties({
        dados: ponto.propriedades,
        rotulo: ponto.rotulo,
        ehCluster: (ponto.propriedades.quantidade ?? 1) > 1,
      });
      feicoes.push(feicao);
    }

    this.fonte.addFeatures(feicoes);
  }

  private estiloDa(feicao: Feature): Style {
    const dados = feicao.get('dados') as PropriedadesDaFeicao | undefined;
    const quantidade = dados?.quantidade ?? 0;

    if (feicao.get('ehPoligono') === true) {
      return new Style({
        stroke: new Stroke({ color: '#14506b', width: 1.5 }),
        fill: new Fill({ color: 'rgba(28, 107, 143, 0.35)' }),
      });
    }

    if (quantidade > 1) {
      // Raio cresce com a raiz da contagem: a AREA do circulo fica proporcional
      // a quantidade, que e como o olho compara tamanhos.
      const raio = Math.min(28, 10 + Math.sqrt(quantidade) * 1.6);

      return new Style({
        image: new Circle({
          radius: raio,
          fill: new Fill({ color: 'rgba(20, 80, 107, 0.85)' }),
          stroke: new Stroke({ color: '#ffffff', width: 2 }),
        }),
        text: new Text({
          text: String(quantidade),
          fill: new Fill({ color: '#ffffff' }),
          font: '600 12px system-ui, sans-serif',
        }),
      });
    }

    return new Style({
      image: new Circle({
        radius: 6,
        fill: new Fill({ color: '#1c6b8f' }),
        stroke: new Stroke({ color: '#ffffff', width: 2 }),
      }),
    });
  }

  /** O OpenLayers tipa o evento como a uniao dos originais possiveis; o clique so usa `pixel`. */
  private aoClicar(evento: MapBrowserEvent<PointerEvent | KeyboardEvent | WheelEvent>): void {
    const mapa = this.mapa;
    if (mapa === null) {
      return;
    }

    const feicao = mapa.forEachFeatureAtPixel(evento.pixel, (encontrada) => encontrada as Feature);

    if (feicao === undefined || feicao === null) {
      this.selecionado.set(null);
      return;
    }

    const dados = feicao.get('dados') as PropriedadesDaFeicao | undefined;
    if (dados === undefined) {
      this.selecionado.set(null);
      return;
    }

    // Clicar em um agregado aproxima em vez de abrir popup: nao ha um imovel
    // unico para mostrar ali.
    if ((dados.quantidade ?? 1) > 1) {
      const view = mapa.getView();
      view.animate({ center: evento.coordinate, zoom: (view.getZoom() ?? ZOOM_INICIAL) + 2, duration: 250 });
      this.selecionado.set(null);
      return;
    }

    this.selecionado.set({ propriedades: dados });
  }

  fecharDetalhe(): void {
    this.selecionado.set(null);
  }

  irParaImovel(id: number): void {
    void this.router.navigate(['/imoveis', id, 'editar']);
  }
}
