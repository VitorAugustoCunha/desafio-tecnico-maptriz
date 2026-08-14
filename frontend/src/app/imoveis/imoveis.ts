import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-imoveis',
  imports: [CommonModule, FormsModule],
  templateUrl: './imoveis.html',
  styleUrl: './imoveis.scss'
})
export class Imoveis implements OnInit {

  imoveis: any = [];
  carregando: any = false;
  mensagem: any = '';
  editandoId: any = null;

  form: any = {
    proprietario: '',
    municipio: '',
    uf: '',
    bairro: '',
    rua: '',
    numero: '',
    latitude: null,
    longitude: null,
    areaM2: null,
    ativo: true
  };

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) { }

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.carregando = true;

    this.http.get('http://localhost:8080/api/imoveis').subscribe((res: any) => {
      this.imoveis = res;
      this.carregando = false;
      console.log('imoveis', res);
      this.cdr.detectChanges();
    });
  }

  salvar() {
    if (this.editandoId != null) {
      this.http.put('http://localhost:8080/api/imoveis/' + this.editandoId, this.form).subscribe((res: any) => {
        this.mensagem = 'Imóvel atualizado!';

        this.http.get('http://localhost:8080/api/imoveis').subscribe((r: any) => {
          this.imoveis = r;
          this.limpar();
          this.cdr.detectChanges();
        });
      });
    } else {
      this.http.post('http://localhost:8080/api/imoveis', this.form).subscribe((res: any) => {
        this.mensagem = 'Imóvel cadastrado!';

        this.http.get('http://localhost:8080/api/imoveis').subscribe((r: any) => {
          this.imoveis = r;
          this.limpar();
          this.cdr.detectChanges();
        });
      });
    }
  }

  editar(i: any) {
    this.editandoId = i.id;
    this.form = i;
    window.scrollTo(0, 0);
  }

  excluir(i: any) {
    if (confirm('Excluir o imóvel de ' + i.proprietario + '?') == false) {
      return;
    }

    this.http.delete('http://localhost:8080/api/imoveis/' + i.id).subscribe((res: any) => {
      this.mensagem = 'Imóvel excluído!';

      this.http.get('http://localhost:8080/api/imoveis').subscribe((r: any) => {
        this.imoveis = r;
        this.cdr.detectChanges();
      });
    });
  }

  limpar() {
    this.editandoId = null;
    this.form = {
      proprietario: '',
      municipio: '',
      uf: '',
      bairro: '',
      rua: '',
      numero: '',
      latitude: null,
      longitude: null,
      areaM2: null,
      ativo: true
    };
  }

  endereco(i: any) {
    return i.rua + ', ' + i.numero + ' - ' + i.bairro;
  }

  totalArea() {
    let total = 0;
    for (let i = 0; i < this.imoveis.length; i++) {
      if (this.imoveis[i].areaM2 != null) {
        total = total + Number(this.imoveis[i].areaM2);
      }
    }
    return total.toFixed(2);
  }
}
