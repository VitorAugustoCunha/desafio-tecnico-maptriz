import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { NotificacoesComponent } from './core/ui/notificacoes.component';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificacoesComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly titulo = 'WebGIS';
}
