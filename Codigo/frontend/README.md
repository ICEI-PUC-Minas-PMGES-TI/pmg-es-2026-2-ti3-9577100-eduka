# Frontend — Eduka

SPA em React 19 + TypeScript, com build pelo Vite, rotas pelo React Router 7 e lint pelo Oxlint.
Não usa biblioteca de UI: os componentes são próprios e estilizados com CSS Modules.

## Comandos

A partir de `Codigo/frontend/` (Node 20+):

```bash
npm install
npm run dev      # servidor de desenvolvimento em http://localhost:5173
npm run build    # checagem de tipos (tsc -b) + build de produção em dist/
npm run lint     # oxlint
npm test         # testes de tela (Vitest); npm run test:watch reexecuta ao salvar
npm run preview  # serve o build localmente
```

Os testes de tela ficam ao lado do componente (`Componente.test.tsx`) e rodam no jsdom, sem
navegador nem backend. `simularApi` (em `src/test/api.ts`) troca só o `fetch`: a tela passa pelo
`apiRequest` de verdade, inclusive o `ProblemDetail` com `codigo` que vira `ApiError`. Para as
telas que consultam o backend em intervalos (a espera do PIX), use os timers falsos do Vitest
(`vi.useFakeTimers({ shouldAdvanceTime: true })`) e avance o relógio, como em
`ModalPagamentoFatura.test.tsx`.

No Docker de desenvolvimento, o código é copiado para a imagem no build. Depois de alterar o
frontend, reconstrua o container: `docker compose -f Codigo/docker-compose.yml up -d --build frontend`.

## Estrutura

```text
src/
├── main.tsx, App.tsx     Entrada da aplicação
├── routes/               router.tsx (rotas), paths.ts (caminhos) e RotaProtegida.tsx
├── roles/                Um módulo por perfil de usuário, cada um com routes.tsx, layouts/,
│   │                     pages/ e, quando precisa, components/ e hooks/ próprios
│   ├── super_admin/      LoginAdmin, painel de escolas (PainelEscolas) e EditarEscola
│   ├── master/           Início (resumo da escola e do financeiro) e Recebimentos
│   ├── secretaria/       Início, Alunos, Professores, Acadêmico (períodos, cursos,
│   │                     disciplinas e turmas), Alocação de professores e Matrículas
│   ├── professor/        Início, Minhas turmas, Avaliações e Notas
│   ├── aluno/            Boletim, Provas e tarefas e Meus dados
│   └── financeiro/       Início (resumo financeiro), Cobranças, Nova cobrança e Despesas
├── pages/                Telas fora dos perfis: Home (página inicial), Login (escola, com
│                         código por e-mail e primeiro acesso), LoginDev, AreaEmConstrucao,
│                         PaginaNaoEncontrada
├── components/           Componentes compartilhados (ver "Componentes" abaixo)
├── hooks/                useCarregamento (busca com cancelamento e recarga), useDetalhe
│                         (detalhe ao abrir um modal), usePopoverFlutuante (dropdowns e calendário)
├── services/             api.ts, authService.ts e um service por recurso da API (escola,
│                         aluno, professor, acadêmico, alocação, matrícula, dashboard, portais)
├── types/                Tipos que espelham os DTOs do backend
├── utils/formatters.ts   Máscaras, validação e formatadores (CPF/CNPJ, CEP, telefone, pontos, plural, data de hoje)
├── styles/global.css     Tokens de cor, fontes, raios, sombras e animações
└── assets/               Logos (cópias de Artefatos/identidade-visual/)
```

Código usado por mais de um perfil fica em `components/`, `hooks/`, `services/`, `types/` ou
`utils/`, nunca dentro da pasta de outro perfil.

## Rotas e autenticação

* `/` é a página inicial pública (`Home`). O login da escola (master, secretaria, financeiro,
  professor e aluno) fica em `/login`; o do `SUPER_ADMIN`, em `/login/admin`.
* O login da escola segue a verificação em duas etapas do backend: com a senha certa, a tela pede
  o código de 6 dígitos enviado por e-mail (`VerificacaoCodigo`); com senha provisória, pede a
  troca de senha do primeiro acesso. Depois o usuário vai para a área do próprio perfil
  (`rotaInicialPorPerfil` em `routes/paths.ts`).
* Cada perfil tem uma rota-mãe (`/super-admin`, `/master`, `/secretaria`, `/financeiro`,
  `/professor`, `/aluno`) guardada por `RotaProtegida`. Sem sessão, ela manda para o login; com
  sessão de outro perfil, manda para a área do próprio usuário. É só conveniência de navegação:
  quem garante o acesso é o backend, que responde `403`.
* `/login/dev` (só em dev): um botão por perfil que entra com as contas de teste do `DevDataSeeder` e abre a área do perfil. A rota só é registrada quando `import.meta.env.DEV` é verdadeiro, então não existe no build de produção. Com a verificação em duas etapas ligada, o login de um clique não funciona: use `LOGIN_VERIFICACAO_HABILITADA=false` no `.env`.
* Use sempre as constantes de `paths` em vez de strings de rota soltas.

## Componentes

Toda tela logada segue o mesmo esqueleto. Antes de criar um componente, veja se um destes já
resolve:

| Componente | Para quê |
|---|---|
| `PainelLayout` | Casca de toda tela logada: marca, título, subtítulo, "Ajuda", "Sair" e o menu flutuante. Cada perfil tem um `*Layout` em `roles/<perfil>/layouts/` que só define o contexto e os itens do menu. |
| `StatCards` + `StatCard` | Faixa de números no topo de listagens e dashboards (até 4 por linha; `destaque` para o principal, `alerta` em vermelho, `atencao` em âmbar). |
| `Dashboard` | Peças das telas de Início: `Painel`, `Colunas`, `Medidor`, `Linhas`, `Atalhos`, `GraficoBarras`, `GraficoComparativo` (duas séries lado a lado, com legenda), `PainelVazio` e `EstadoDashboard`. |
| `PainelFinanceiro` | Blocos do resumo financeiro (`GET /dashboard/financeiro`), comuns ao Início do financeiro e ao do `MASTER`: `EntrouESaiu`, `VencemNaSemana`, `UltimosPagamentos` e `UltimasAcoes`. |
| `Modal` | Casca de todo diálogo: título, botão de fechar, corpo com rolagem e rodapé (`tamanho` pequeno, médio ou grande; `ocupado` impede fechar enquanto salva). Com `onEnviar`, vira formulário. |
| `ModalCadastro` | Formulário curto em duas colunas, com Cancelar/Salvar e tratamento do erro de envio. |
| `ModalDetalhes` | Detalhe somente leitura, em seções de rótulo/valor. |
| `ConfirmarExclusao` | Pergunta antes de excluir; aceita texto próprio e uma ação alternativa (ex.: "Inativar em vez disso"). |
| `Button` | Variantes `primary`/`accent` (roxo), `outline` (vidro), `danger` (excluir confirmado) e `dangerOutline` (excluir na linha da tabela). Sem texto, fica compacto. |
| `Campo`, `Select`, `CampoData`, `Switch` | Campos de formulário com rótulo, dica e erro. Use `Select` e `CampoData` em vez dos controles nativos do navegador. |
| `Badge`, `Icon` | Situação (ativo, suspenso...) e ícones SVG. |

Não use `window.alert` nem `window.confirm`: avisos e confirmações são diálogos com `Modal`.

## Comunicação com a API

Nenhuma tela chama `fetch` diretamente. Tudo passa por `apiRequest` (`services/api.ts`), que:

* usa como base `VITE_API_URL` ou, se ausente, `http://localhost:8080` em dev e `/api` em prod;
* injeta `Authorization: Bearer <token>`, lido do `localStorage` (chave `eduka.accessToken`);
* transforma respostas de erro e falha de conexão em `ApiError` (com o `status` HTTP e uma
  mensagem pronta para exibir).

## Estilo

* **Tokens em `styles/global.css`.** Cores, raios e sombras são variáveis CSS (`--color-primary`,
  `--color-primary-text`, `--color-veil`, `--shadow-lg`...). Não use cores fixas nos módulos.
* **Liquid glass:** cards, botões secundários, filtros e a navegação são superfícies de vidro sobre o
  fundo lavanda do `body`. Use `--glass-bg` (ou `--glass-bg-tint` para o card em destaque),
  `--glass-border`, `--glass-shadow` e `--glass-blur`; o botão principal usa
  `--button-primary-bg` e `--button-primary-shadow`.
* **Fontes:** Nunito 800/900 em títulos, números, rótulos e botões (`--font-display`); Figtree no
  texto e nos campos (`--font-sans`). Carregadas do Google Fonts em `index.html`.
* **Tema:** apenas claro. Não há modo escuro.
* **Animações:** curtas e discretas, expostas como variáveis (`--animacao-surgir`,
  `--animacao-entrar`, `--animacao-aparecer`), porque o CSS Modules renomeia `@keyframes` por
  arquivo. São desligadas quando o sistema pede movimento reduzido (`prefers-reduced-motion`).
* **Vidro opaco** (`--glass-bg-opaque`, `--glass-shadow-opaque`) em tudo que abre por cima de
  campos e textos: modais, menus do `Select` e calendário do `CampoData`.
* **Âmbar** (`--color-warning`) para pendência que não é erro; vermelho (`--color-danger`) para erro
  e ação destrutiva.
