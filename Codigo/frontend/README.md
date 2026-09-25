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
npm run preview  # serve o build localmente
```

No Docker de desenvolvimento, o código é copiado para a imagem no build. Depois de alterar o
frontend, reconstrua o container: `docker compose -f Codigo/docker-compose.yml up -d --build frontend`.

## Estrutura

```text
src/
├── main.tsx, App.tsx     Entrada da aplicação
├── routes/               router.tsx (rotas), paths.ts (caminhos) e RotaProtegida.tsx
├── roles/                Um módulo por perfil de usuário
│   ├── super_admin/      pages · layouts · components · hooks · routes.tsx
│   └── master, secretaria, financeiro, professor, aluno/
│                         Só routes.tsx, apontando para a tela "Área em construção"
├── pages/                Telas comuns: AreaEmConstrucao, PaginaNaoEncontrada
├── components/           Componentes compartilhados: Badge, Button, Campo, CampoData,
│                         Icon, Select, StatCard, Switch
├── hooks/                usePopoverFlutuante (posiciona dropdowns e o calendário)
├── services/             api.ts, authService.ts, escolaService.ts
├── types/, utils/        Tipos de domínio e formatadores/máscaras (CNPJ/CPF, CEP, telefone...)
├── styles/global.css     Tokens de cor, fontes, raios, sombras e animações
└── assets/               Logos (cópias de Artefatos/identidade-visual/)
```

## Rotas e autenticação

* `/` redireciona para `/login` (tela `LoginAdmin`). Após o login, o usuário vai para a área do
  próprio perfil (`rotaInicialPorPerfil` em `routes/paths.ts`). O `SUPER_ADMIN` vai para
  `/super-admin/escolas`.
* Cada perfil tem uma rota-mãe (`/super-admin`, `/master`, `/secretaria`, `/financeiro`,
  `/professor`, `/aluno`) guardada por `RotaProtegida`. Sem sessão, ela manda para o login; com
  sessão de outro perfil, manda para a área do próprio usuário. É só conveniência de navegação:
  quem garante o acesso é o backend, que responde `403`.
* Telas do Super Admin: painel de escolas (lista com busca, filtros por plano e situação,
  cadastro em modal, ativar/suspender) e edição de escola (`/super-admin/escolas/:id/editar`).
* Use sempre as constantes de `paths` em vez de strings de rota soltas.

## Comunicação com a API

Nenhuma tela chama `fetch` diretamente. Tudo passa por `apiRequest` (`services/api.ts`), que:

* usa como base `VITE_API_URL` ou, se ausente, `http://localhost:8080` em dev e `/api` em prod;
* injeta `Authorization: Bearer <token>`, lido do `localStorage` (chave `eduka.accessToken`);
* transforma respostas de erro e falha de conexão em `ApiError` (com o `status` HTTP e uma
  mensagem pronta para exibir).

## Estilo

* **Tokens em `styles/global.css`.** Cores, raios e sombras são variáveis CSS (`--color-primary`,
  `--color-on-primary`, `--color-dark`, `--shadow-lg`...). Não use cores fixas nos módulos.
* **Fontes:** Nunito 900 em títulos e na palavra "eduka" (`--font-display`); Atkinson Hyperlegible
  no texto (`--font-sans`). Carregadas do Google Fonts em `index.html`.
* **Tema:** apenas claro. Não há modo escuro.
* **Animações:** curtas e discretas, expostas como variáveis (`--animacao-surgir`,
  `--animacao-entrar`, `--animacao-aparecer`), porque o CSS Modules renomeia `@keyframes` por
  arquivo. São desligadas quando o sistema pede movimento reduzido (`prefers-reduced-motion`).
* Campos de data usam `CampoData` (calendário do `react-day-picker` no visual da página) e listas
  de opção usam `Select`, em vez dos controles nativos do navegador.
