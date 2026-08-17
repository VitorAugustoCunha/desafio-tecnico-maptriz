import TileLayer from 'ol/layer/Tile';
import { OSM, XYZ } from 'ol/source';

/**
 * Camadas de base do mapa.
 *
 * <p><b>Por que imagem aérea existe aqui.</b> Para contornar um lote é preciso
 * ver o terreno. O mapa de ruas do OpenStreetMap mostra vias e edificações já
 * desenhadas por outra pessoa — não a realidade do imóvel. Num cadastro
 * multifinalitário, quem digitaliza o lote traça sobre ortofoto ou imagem de
 * satélite; sem isso, "desenhar o imóvel" vira chute.
 *
 * <p>A imagem vem do serviço World Imagery da Esri, que é aberto para uso com
 * atribuição. Num sistema em produção municipal, o natural seria trocar por um
 * WMS da própria prefeitura (ortofoto oficial, mais recente e com precisão
 * cadastral) — a troca é de uma linha, por isso as camadas ficam isoladas aqui.
 */

export type TipoDeCamadaBase = 'satelite' | 'ruas';

const ATRIBUICAO_ESRI =
  'Imagens © <a href="https://www.esri.com" target="_blank" rel="noopener">Esri</a>, ' +
  'Maxar, Earthstar Geographics e a comunidade de usuários GIS';

/** Ortofoto / satélite. É a base usada para desenhar o lote. */
export function camadaDeSatelite(): TileLayer<XYZ> {
  return new TileLayer({
    source: new XYZ({
      // Note a ordem {z}/{y}/{x} — o serviço da Esri inverte y e x em relação
      // ao padrão XYZ do OSM. Trocar a ordem devolve tiles do lugar errado.
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      attributions: ATRIBUICAO_ESRI,
      maxZoom: 19,
      crossOrigin: 'anonymous',
    }),
    properties: { tipo: 'satelite' },
  });
}

/** Mapa de ruas. Melhor para se localizar por nome de via. */
export function camadaDeRuas(): TileLayer<OSM> {
  return new TileLayer({
    // A fonte OSM já injeta a atribuição exigida pela licença ODbL.
    source: new OSM({ crossOrigin: 'anonymous' }),
    properties: { tipo: 'ruas' },
  });
}
