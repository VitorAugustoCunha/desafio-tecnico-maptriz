import { Routes } from '@angular/router';
import { Imoveis } from './imoveis/imoveis';

export const routes: Routes = [
  { path: '', component: Imoveis },
  { path: 'imoveis', component: Imoveis },
  { path: '**', component: Imoveis }
];
