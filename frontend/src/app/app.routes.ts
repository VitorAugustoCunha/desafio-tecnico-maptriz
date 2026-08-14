import { Routes } from '@angular/router';

/**
 * Rotas da aplicacao.
 *
 * <p>No codigo original as tres rotas (`''`, `imoveis` e `**`) apontavam para o
 * mesmo componente: nao havia navegacao de verdade, e o coringa escondia
 * qualquer 404.
 *
 * <p>Todas com carregamento sob demanda: quem so usa a listagem nao baixa o
 * OpenLayers, que e a maior dependencia do projeto.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'imoveis' },

  {
    path: 'imoveis',
    title: 'Imóveis — WebGIS',
    loadComponent: () =>
      import('./features/imoveis/lista/imoveis-lista.component').then((m) => m.ImoveisListaComponent),
  },
  {
    path: 'imoveis/novo',
    title: 'Novo imóvel — WebGIS',
    loadComponent: () =>
      import('./features/imoveis/form/imovel-form.component').then((m) => m.ImovelFormComponent),
  },
  {
    path: 'imoveis/:id/editar',
    title: 'Editar imóvel — WebGIS',
    loadComponent: () =>
      import('./features/imoveis/form/imovel-form.component').then((m) => m.ImovelFormComponent),
  },

  {
    path: 'proprietarios',
    title: 'Proprietários — WebGIS',
    loadComponent: () =>
      import('./features/proprietarios/lista/proprietarios-lista.component').then(
        (m) => m.ProprietariosListaComponent,
      ),
  },
  {
    path: 'proprietarios/:id',
    title: 'Proprietário — WebGIS',
    loadComponent: () =>
      import('./features/proprietarios/detalhe/proprietario-detalhe.component').then(
        (m) => m.ProprietarioDetalheComponent,
      ),
  },

  {
    path: 'mapa',
    title: 'Mapa — WebGIS',
    loadComponent: () => import('./features/mapa/mapa.component').then((m) => m.MapaComponent),
  },

  // Rota propria de 404, em vez de servir a listagem para qualquer endereco.
  {
    path: '**',
    title: 'Página não encontrada — WebGIS',
    loadComponent: () =>
      import('./features/nao-encontrado/nao-encontrado.component').then((m) => m.NaoEncontradoComponent),
  },
];
