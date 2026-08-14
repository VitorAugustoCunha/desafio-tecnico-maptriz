import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-nao-encontrado',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="cartao">
      <div class="estado">
        <h1>Página não encontrada</h1>
        <p>O endereço acessado não existe nesta aplicação.</p>
        <a class="botao botao--primario" routerLink="/imoveis">Ir para a listagem de imóveis</a>
      </div>
    </section>
  `,
})
export class NaoEncontradoComponent {}
