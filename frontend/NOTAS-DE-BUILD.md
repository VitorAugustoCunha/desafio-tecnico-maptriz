# Notas de build do frontend

Duas opções não óbvias em `angular.json`, registradas aqui porque o arquivo é
JSON e não aceita comentários.

## `optimization.styles.inlineCritical: false`

O *critical CSS inlining* do Angular é ligado por padrão em produção. Ele injeta
o CSS acima da dobra num `<style>` e adia o resto assim:

```html
<link rel="stylesheet" href="styles-XXXX.css" media="print" onload="this.media='all'">
```

Esse `onload` é um **handler inline**. Com a CSP em `script-src 'self'`
(sem `unsafe-inline`), o navegador bloqueia a execução — o `media` nunca vira
`all` e **a folha de estilos global jamais é aplicada**. O resultado é uma tela
que sobe sem o design system inteiro, e o único sinal é uma linha no console:

```
Executing inline event handler violates the following Content Security Policy
directive 'script-src 'self''. The action has been blocked.
```

O `<noscript>` de fallback que o Angular também emite só entra em cena com
JavaScript desabilitado, então não cobre este caso.

Havia duas saídas: afrouxar a CSP com `unsafe-hashes`/`unsafe-inline`, ou abrir
mão do inline critical. Preferi manter a CSP estrita — o ganho do inline
critical é de milissegundos na primeira pintura, e não vale enfraquecer a
política que protege contra injeção de script.

## `budgets`

O limite inicial é de 700 kB (aviso) e 1,5 MB (erro), acima do padrão do
Angular. O OpenLayers é grande, mas **não entra no bundle inicial**: fica no
chunk lazy da rota `/mapa` e do editor de lote. O bundle inicial medido está em
~286 kB. Os limites existem para pegar regressão — se algum import acidental
puxar o OpenLayers para o caminho crítico, o build falha em vez de passar
despercebido.
