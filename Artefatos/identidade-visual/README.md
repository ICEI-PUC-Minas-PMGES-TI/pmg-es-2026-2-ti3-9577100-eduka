# Identidade visual — Eduka

Kit completo da marca. Este diretório é a **fonte da verdade**: os arquivos usados pela aplicação
são cópias dele em `Codigo/frontend/`. Ao alterar uma logo aqui, atualize também a cópia
correspondente na tabela abaixo.

## Variantes

| Arquivo | O que é | Onde usar |
|---|---|---|
| `eduka-logo.svg` / `.png` | Logo completa (ícone + palavra "eduka" em grafite) | Fundos claros |
| `eduka-logo-branca.svg` / `.png` | Logo completa com a palavra em branco | Fundos escuros ou coloridos |
| `eduka-icone.svg` | Apenas o ícone (quadrado arredondado roxo com livro) | Espaços pequenos: cabeçalho, avatar, favicon |
| `eduka-icone-512.png` | Ícone em 512×512 | Ícone de aplicativo, PWA, prévia em redes sociais |
| `favicon-32.png` | Ícone em 32×32 | Favicon do navegador |
| `favicon-180.png` | Ícone em 180×180 | `apple-touch-icon` (iOS) |
| `eduka-logo-sobre-preto.png` / `.svg` | Demonstração da logo branca sobre fundo preto | **Referência apenas** — tem o fundo embutido, não use na interface |
| `eduka-logo-sobre-roxo.png` / `.svg` | Demonstração da logo branca sobre fundo roxo | **Referência apenas** — tem o fundo embutido, não use na interface |

Prefira os `.svg` na interface: escalam sem perder nitidez e pesam menos.
Os `.png` servem para onde SVG não é aceito (documentos, apresentações, redes sociais).

## Onde cada arquivo está em uso

| Origem | Cópia na aplicação | Uso |
|---|---|---|
| `eduka-icone.svg` | `Codigo/frontend/src/assets/eduka-icone.svg` | Ícone no cabeçalho do Super Admin |
| `eduka-logo.svg` | `Codigo/frontend/src/assets/eduka-logo.svg` | Logo completa em telas de fundo claro |
| `eduka-logo-branca.svg` | `Codigo/frontend/src/assets/eduka-logo-branca.svg` | Logo completa em telas de fundo escuro |
| `favicon-32.png` | `Codigo/frontend/public/favicon-32.png` | `<link rel="icon">` |
| `favicon-180.png` | `Codigo/frontend/public/favicon-180.png` | `<link rel="apple-touch-icon">` |
| `eduka-icone-512.png` | `Codigo/frontend/public/eduka-icone-512.png` | Ícone de aplicativo / prévia |

## Tipografia

| Fonte | Uso | Token no frontend |
|---|---|---|
| **Nunito** (peso 900) | Títulos, números grandes e a palavra "eduka" da logo | `--font-display` |
| **Atkinson Hyperlegible** (400 e 700) | Texto da interface | `--font-sans` |

As duas vêm do Google Fonts, carregadas em `Codigo/frontend/index.html`. A interface usa apenas
tema claro.

## Cor da marca

O roxo dos arquivos de logo é **`#6D3FD6`**. O token `--color-primary` do frontend
(`Codigo/frontend/src/styles/global.css`) é **`#6c4ce0`** — próximo, mas não idêntico.
Vale alinhar os dois antes de fechar a identidade visual.
