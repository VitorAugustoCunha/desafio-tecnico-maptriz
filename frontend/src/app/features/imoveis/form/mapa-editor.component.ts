import { DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import Feature from 'ol/Feature';
import Map from 'ol/Map';
import View from 'ol/View';
import { Attribution, defaults as controlesPadrao } from 'ol/control';
import { Draw, Modify, Snap } from 'ol/interaction';
import { Point, Polygon } from 'ol/geom';
import TileLayer from 'ol/layer/Tile';
import { Vector as CamadaVetorial } from 'ol/layer';
import { fromLonLat, toLonLat } from 'ol/proj';
import { OSM, XYZ, Vector as FonteVetorial } from 'ol/source';
import { Circle, Fill, Stroke, Style } from 'ol/style';
import { MapBrowserEvent } from 'ol';

import { PoligonoGeoJson, Posicao } from '../../../core/models/imovel.model';
import { TipoDeCamadaBase, camadaDeRuas, camadaDeSatelite } from '../../mapa/gis/camadas-base';
import { areaEmMetrosQuadrados, centroDoAnel, retanguloEmTornoDe } from '../../mapa/gis/projecao';

export type ModoDeDesenho = 'dimensoes' | 'desenho';

/** Centro do Brasil, usado quando ainda não há ponto escolhido. */
const CENTRO_PADRAO: Posicao = [-51.9, -14.2];
const ZOOM_PADRAO = 4;
const ZOOM_AO_ESCOLHER = 17;

/**
 * Mapa de edição do lote.
 *
 * <p>Dois modos, porque são duas formas legítimas de descrever um imóvel:
 *
 * <ul>
 *   <li><b>dimensões</b> — clicar posiciona o centro; o retângulo aparece na
 *       hora, calculado com a mesma projeção do servidor (EPSG:31982), então o
 *       que se vê é o que será gravado;</li>
 *   <li><b>desenho</b> — traçar o lote vértice a vértice, para os casos (a
 *       maioria, num cadastro real) em que o terreno não é um retângulo
 *       alinhado aos eixos.</li>
 * </ul>
 *
 * <p>O componente não conhece formulário nem API: recebe valores e emite
 * intenção. Isso o mantém testável e reutilizável.
 */
@Component({
  selector: 'app-mapa-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="editor">
      <div class="editor__barra" role="group" aria-label="Como definir o lote">
        <button
          type="button"
          class="aba"
          [class.aba--ativa]="modo() === 'dimensoes'"
          [attr.aria-pressed]="modo() === 'dimensoes'"
          (click)="trocarModo('dimensoes')"
        >
          Ponto + dimensões
        </button>
        <button
          type="button"
          class="aba"
          [class.aba--ativa]="modo() === 'desenho'"
          [attr.aria-pressed]="modo() === 'desenho'"
          (click)="trocarModo('desenho')"
        >
          Desenhar no mapa
        </button>

        <div class="editor__direita">
          @if (modo() === 'desenho' && temDesenho()) {
            <button type="button" class="botao botao--pequeno botao--neutro" (click)="limparDesenho()">
              Apagar desenho
            </button>
          }

          <!-- Sobre imagem aérea dá para contornar o lote; sobre mapa de ruas, não. -->
          <label class="editor__base">
            <span class="visualmente-oculto">Camada de fundo</span>
            <select [value]="camadaBase()" (change)="trocarCamadaBase($event)">
              <option value="satelite">Satélite</option>
              <option value="ruas">Ruas</option>
            </select>
          </label>
        </div>
      </div>

      <div #mapa class="editor__mapa" role="application" [attr.aria-label]="rotuloDoMapa()"></div>

      <p class="editor__ajuda">
        @if (modo() === 'dimensoes') {
          Clique no mapa para posicionar o centro do lote. O retângulo usa a largura e o
          comprimento informados acima, na mesma projeção do servidor (EPSG:31982).
        } @else if (temDesenho()) {
          Arraste os vértices para ajustar. Área aproximada:
          <strong>{{ areaDesenhada() | number: '1.0-0' }} m²</strong> — o valor definitivo é
          calculado pelo PostGIS ao salvar.
        } @else {
          Clique para marcar cada vértice do lote e clique no primeiro ponto (ou dê duplo
          clique) para fechar.
        }
      </p>
    </div>
  `,
  styles: `
    .editor {
      border: 1px solid var(--borda);
      border-radius: var(--raio);
      overflow: hidden;
    }

    .editor__barra {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      padding: 0.5rem;
      background: var(--superficie-alt);
      border-bottom: 1px solid var(--borda);
    }

    .aba {
      padding: 0.4rem 0.9rem;
      border: 1px solid transparent;
      border-radius: var(--raio);
      background: none;
      font: inherit;
      font-size: 0.875rem;
      cursor: pointer;
      color: var(--texto-suave);
    }

    .aba:hover {
      background: var(--superficie);
    }

    .aba--ativa {
      background: var(--superficie);
      border-color: var(--borda);
      color: var(--texto);
      font-weight: 600;
    }

    .editor__direita {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-left: auto;
    }

    .editor__base select {
      padding: 0.3rem 0.5rem;
      border: 1px solid var(--borda);
      border-radius: var(--raio);
      background: var(--superficie);
      font: inherit;
      font-size: 0.8125rem;
    }

    .editor__mapa {
      width: 100%;
      height: 22rem;
    }

    .editor__ajuda {
      margin: 0;
      padding: 0.6rem 0.75rem;
      font-size: 0.8125rem;
      color: var(--texto-suave);
      background: var(--superficie-alt);
      border-top: 1px solid var(--borda);
      line-height: 1.45;
    }
  `,
  imports: [DecimalPipe],
})
export class MapaEditorComponent implements AfterViewInit, OnDestroy {
  readonly latitude = input<number | null>(null);
  readonly longitude = input<number | null>(null);
  readonly larguraM = input<number | null>(null);
  readonly comprimentoM = input<number | null>(null);
  readonly geometriaInicial = input<PoligonoGeoJson | null>(null);

  readonly modo = signal<ModoDeDesenho>('dimensoes');

  readonly pontoEscolhido = output<{ latitude: number; longitude: number }>();
  readonly poligonoAlterado = output<PoligonoGeoJson | null>();
  readonly modoAlterado = output<ModoDeDesenho>();

  private readonly elemento = viewChild.required<ElementRef<HTMLDivElement>>('mapa');

  private mapa: Map | null = null;
  private base: TileLayer<XYZ | OSM> | null = null;
  private observador: ResizeObserver | null = null;
  private readonly fonte = new FonteVetorial();

  readonly camadaBase = signal<TipoDeCamadaBase>('satelite');
  private desenho: Draw | null = null;
  private modificacao: Modify | null = null;
  private encaixe: Snap | null = null;

  readonly temDesenho = signal(false);
  readonly areaDesenhada = signal(0);

  /** Evita que o mapa reaja ao próprio evento que ele acabou de emitir. */
  private aplicandoDoExterior = false;

  constructor() {
    // Ponto ou dimensões mudaram no formulário: redesenha a pré-visualização.
    effect(() => {
      const lat = this.latitude();
      const lon = this.longitude();
      const largura = this.larguraM();
      const comprimento = this.comprimentoM();

      if (this.modo() !== 'dimensoes' || this.mapa === null) {
        return;
      }

      this.desenharPreVisualizacao(lon, lat, largura, comprimento);
    });

    // Geometria carregada na edição: entra em modo desenho já com o lote.
    effect(() => {
      const inicial = this.geometriaInicial();
      if (inicial === null || this.mapa === null) {
        return;
      }
      this.aplicarGeometriaInicial(inicial);
    });
  }

  ngAfterViewInit(): void {
    this.montar();
  }

  ngOnDestroy(): void {
    this.observador?.disconnect();
    this.observador = null;
    this.mapa?.setTarget(undefined);
    this.mapa = null;
  }

  /** Troca a base entre satélite e ruas, preservando o desenho e o enquadramento. */
  trocarCamadaBase(evento: Event): void {
    const escolha = (evento.target as HTMLSelectElement).value as TipoDeCamadaBase;

    if (this.mapa === null || this.base === null || escolha === this.camadaBase()) {
      return;
    }

    this.mapa.removeLayer(this.base);
    this.base = escolha === 'satelite' ? camadaDeSatelite() : camadaDeRuas();
    // Índice 0: a base tem que ficar embaixo da camada de desenho.
    this.mapa.getLayers().insertAt(0, this.base);

    this.camadaBase.set(escolha);
  }

  rotuloDoMapa(): string {
    return this.modo() === 'dimensoes'
      ? 'Mapa para posicionar o centro do lote'
      : 'Mapa para desenhar o contorno do lote';
  }

  trocarModo(novo: ModoDeDesenho): void {
    if (this.modo() === novo) {
      return;
    }

    this.modo.set(novo);
    this.modoAlterado.emit(novo);

    this.fonte.clear();
    this.temDesenho.set(false);
    this.areaDesenhada.set(0);

    if (novo === 'desenho') {
      // Trocar para desenho descarta o retângulo: as formas são excludentes,
      // e o servidor recusa as duas juntas.
      this.poligonoAlterado.emit(null);
      this.ligarDesenho();
    } else {
      this.desligarDesenho();
      this.poligonoAlterado.emit(null);
      this.desenharPreVisualizacao(this.longitude(), this.latitude(), this.larguraM(), this.comprimentoM());
    }
  }

  limparDesenho(): void {
    this.fonte.clear();
    this.temDesenho.set(false);
    this.areaDesenhada.set(0);
    this.poligonoAlterado.emit(null);
    this.ligarDesenho();
  }

  private montar(): void {
    const container = this.elemento().nativeElement;

    // Satélite por padrão: é a base sobre a qual dá para contornar o lote.
    this.base = camadaDeSatelite();

    this.mapa = new Map({
      target: container,
      layers: [this.base, new CamadaVetorial({ source: this.fonte, style: () => this.estilo() })],
      view: new View({
        center: fromLonLat([...this.centroInicial()]),
        zoom: this.temPonto() ? ZOOM_AO_ESCOLHER : ZOOM_PADRAO,
        maxZoom: 21,
      }),
      controls: controlesPadrao({ attribution: false }).extend([new Attribution({ collapsible: false })]),
    });

    // O container pode ter altura 0 no instante em que o mapa é criado — o CSS
    // do componente carrega junto com o chunk da rota, e o OpenLayers mede o
    // elemento na hora. Sem isto o mapa fica em branco com o aviso
    // "No map visible because the map container's width or height are 0".
    // O observer também cobre redimensionamento de janela e troca de aba.
    this.observador = new ResizeObserver(() => {
      this.mapa?.updateSize();
      // renderSync desenha na hora, sem esperar requestAnimationFrame. Sem
      // isto, o primeiro quadro fica pendente quando a aba nao esta
      // compondo (recem-aberta em segundo plano, por exemplo) e o mapa
      // aparece em branco ate o usuario interagir.
      this.mapa?.renderSync();
    });
    this.observador.observe(container);

    this.mapa.on('click', (evento) => this.aoClicar(evento));

    const inicial = this.geometriaInicial();
    if (inicial !== null) {
      this.aplicarGeometriaInicial(inicial);
    } else {
      this.desenharPreVisualizacao(this.longitude(), this.latitude(), this.larguraM(), this.comprimentoM());
    }

    // O estilo do componente carrega junto com o chunk da rota, entao no
    // instante da criacao o container pode ainda ter altura 0. Uma medicao
    // extra no fim da fila de tarefas cobre esse caso.
    setTimeout(() => {
      this.mapa?.updateSize();
      this.mapa?.renderSync();
    });
  }

  private aoClicar(evento: MapBrowserEvent<PointerEvent | KeyboardEvent | WheelEvent>): void {
    // No modo desenho quem trata o clique é a interação Draw.
    if (this.modo() !== 'dimensoes') {
      return;
    }

    const [longitude, latitude] = toLonLat(evento.coordinate);

    this.pontoEscolhido.emit({
      latitude: Number(latitude.toFixed(7)),
      longitude: Number(longitude.toFixed(7)),
    });
  }

  private desenharPreVisualizacao(
    longitude: number | null,
    latitude: number | null,
    largura: number | null,
    comprimento: number | null,
  ): void {
    this.fonte.clear();

    if (longitude === null || latitude === null) {
      return;
    }

    // Sem dimensões ainda: mostra só o ponto escolhido.
    const anel = retanguloEmTornoDe(longitude, latitude, largura ?? 0, comprimento ?? 0);

    if (anel === null) {
      const ponto = new Feature(new Point(fromLonLat([longitude, latitude])));
      ponto.set('tipo', 'ponto');
      this.fonte.addFeature(ponto);
      return;
    }

    const poligono = new Feature(new Polygon([anel.map(([lon, lat]) => fromLonLat([lon, lat]))]));
    poligono.set('tipo', 'retangulo');

    const centro = new Feature(new Point(fromLonLat([longitude, latitude])));
    centro.set('tipo', 'ponto');

    this.fonte.addFeatures([poligono, centro]);
  }

  private ligarDesenho(): void {
    const mapa = this.mapa;
    if (mapa === null || this.desenho !== null) {
      return;
    }

    this.desenho = new Draw({ source: this.fonte, type: 'Polygon' });

    this.desenho.on('drawend', (evento) => {
      const geometria = evento.feature.getGeometry();
      if (geometria instanceof Polygon) {
        // Uma vez fechado o lote, sai do modo "traçar" e entra no de ajustar.
        this.emitirPoligono(geometria);
        queueMicrotask(() => this.desligarSomenteDesenho());
      }
    });

    mapa.addInteraction(this.desenho);

    this.modificacao = new Modify({ source: this.fonte });
    this.modificacao.on('modifyend', () => this.emitirDoPrimeiroPoligono());
    mapa.addInteraction(this.modificacao);

    // Encaixe nos próprios vértices, para fechar o anel sem precisar de mira fina.
    this.encaixe = new Snap({ source: this.fonte });
    mapa.addInteraction(this.encaixe);
  }

  /** Remove só a interação de traçar, mantendo a de ajustar vértices. */
  private desligarSomenteDesenho(): void {
    if (this.mapa !== null && this.desenho !== null) {
      this.mapa.removeInteraction(this.desenho);
      this.desenho = null;
    }
  }

  private desligarDesenho(): void {
    if (this.mapa === null) {
      return;
    }
    for (const interacao of [this.desenho, this.modificacao, this.encaixe]) {
      if (interacao !== null) {
        this.mapa.removeInteraction(interacao);
      }
    }
    this.desenho = null;
    this.modificacao = null;
    this.encaixe = null;
  }

  private emitirDoPrimeiroPoligono(): void {
    const feicao = this.fonte.getFeatures().find((f) => f.getGeometry() instanceof Polygon);
    const geometria = feicao?.getGeometry();

    if (geometria instanceof Polygon) {
      this.emitirPoligono(geometria);
    }
  }

  private emitirPoligono(geometria: Polygon): void {
    if (this.aplicandoDoExterior) {
      return;
    }

    const anel = geometria
      .getCoordinates()[0]
      ?.map((coordenada) => {
        const [lon, lat] = toLonLat(coordenada);
        return [Number(lon.toFixed(7)), Number(lat.toFixed(7))] as Posicao;
      });

    if (anel === undefined || anel.length < 4) {
      return;
    }

    // Garante o anel fechado, como o GeoJSON exige — o OpenLayers já fecha,
    // mas depender disso deixaria a validação do servidor decidir por nós.
    const primeiro = anel[0] as Posicao;
    const ultimo = anel[anel.length - 1] as Posicao;
    if (primeiro[0] !== ultimo[0] || primeiro[1] !== ultimo[1]) {
      anel.push(primeiro);
    }

    this.temDesenho.set(true);
    this.areaDesenhada.set(Math.round(areaEmMetrosQuadrados(anel)));

    const centro = centroDoAnel(anel);
    if (centro !== null) {
      this.pontoEscolhido.emit({
        latitude: Number(centro[1].toFixed(7)),
        longitude: Number(centro[0].toFixed(7)),
      });
    }

    this.poligonoAlterado.emit({ type: 'Polygon', coordinates: [anel] });
  }

  private aplicarGeometriaInicial(geometria: PoligonoGeoJson): void {
    const anel = geometria.coordinates[0];
    if (anel === undefined || anel.length < 4 || this.mapa === null) {
      return;
    }

    this.aplicandoDoExterior = true;

    this.modo.set('desenho');
    this.fonte.clear();

    const poligono = new Polygon([anel.map(([lon, lat]) => fromLonLat([lon, lat]))]);
    this.fonte.addFeature(new Feature(poligono));

    this.temDesenho.set(true);
    this.areaDesenhada.set(Math.round(areaEmMetrosQuadrados(anel)));

    this.mapa.getView().fit(poligono.getExtent(), { padding: [40, 40, 40, 40], maxZoom: 19 });

    // Só ajuste de vértices: o lote já existe, não há o que traçar.
    this.modificacao = new Modify({ source: this.fonte });
    this.modificacao.on('modifyend', () => this.emitirDoPrimeiroPoligono());
    this.mapa.addInteraction(this.modificacao);

    this.encaixe = new Snap({ source: this.fonte });
    this.mapa.addInteraction(this.encaixe);

    this.aplicandoDoExterior = false;
  }

  private estilo(): Style {
    return new Style({
      stroke: new Stroke({ color: '#14506b', width: 2 }),
      fill: new Fill({ color: 'rgba(28, 107, 143, 0.30)' }),
      image: new Circle({
        radius: 5,
        fill: new Fill({ color: '#a32020' }),
        stroke: new Stroke({ color: '#ffffff', width: 2 }),
      }),
    });
  }

  private temPonto(): boolean {
    return this.latitude() !== null && this.longitude() !== null;
  }

  private centroInicial(): Posicao {
    const lat = this.latitude();
    const lon = this.longitude();
    return lat !== null && lon !== null ? [lon, lat] : CENTRO_PADRAO;
  }
}
