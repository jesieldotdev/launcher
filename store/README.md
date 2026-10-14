# Loja de widgets HTML

Catálogo que o app baixa em **Estúdio HTML › Loja**. Não há servidor: o app lê
`store/index.json` direto deste repositório.

## Publicar um widget
1. No app, em Estúdio HTML › Meus widgets, abra o menu ⋮ do widget › **Publicar na loja**.
2. O app abre uma issue `[Loja] …` já preenchida (precisa de uma conta no GitHub).
   Se o widget for grande, o código vai pela área de transferência: é só colar.
3. O dono do repositório confere o código e põe a etiqueta **`aprovado`**.
4. O Action `.github/workflows/store.yml` roda `store/publish.py`, grava
   `store/widgets/<id>.json`, remonta `store/index.json` e fecha a issue.

## Arquivos
- `widgets/*.json`: um arquivo por widget (fonte do catálogo).
- `index.json`: gerado; não edite à mão. Para remontar: `python3 store/publish.py --rebuild`.
- Para tirar um widget da loja: apague o arquivo em `widgets/` e remonte o índice.
