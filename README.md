# Eduka

Plataforma SaaS de gestão escolar que centraliza informações acadêmicas, administrativas e financeiras de várias instituições de ensino em um único sistema multi-tenant, com dados isolados por escola e acesso controlado por perfil de usuário (SUPER_ADMIN, MASTER, SECRETARIA, FINANCEIRO, PROFESSOR e ALUNO).

O MVP cobre cadastro de escolas, usuários, alunos, professores, cursos, turmas e matrículas, além de cobranças, pagamentos e despesas com registro manual. Emissão de boletos, confirmação automática de pagamentos, relatórios avançados e aplicativo mobile ficam para versões futuras. Os requisitos completos estão em [`Artefatos/visão-requisitos/`](Artefatos/visão-requisitos/).

## Alunos integrantes da equipe

* Ítalo Eduardo Carneiro da Silva
* Guilherme Augusto Martins de Carvalho
* Vinícius Matos Oliveira Rocha
* João Victor Vial Leite Soares
* Gabriel Felício Dias

## Professores responsáveis

* Ian Guelman
* Rafael Henriques Nogueira Diniz

## Instruções de utilização

### Executando com Docker (recomendado)

1. Copie o arquivo de variáveis de ambiente de exemplo:

   ```bash
   cp .env.example .env
   ```

   Os valores do exemplo servem para dev. `JWT_SECRET` (mínimo de 32 bytes) e `PII_ENCRYPTION_KEY` (32 bytes em base64) são obrigatórios; gere chaves próprias com `openssl rand -base64 48` e `openssl rand -base64 32`, respectivamente.

2. Suba os serviços (Postgres, pgAdmin, backend e frontend):

   ```bash
   docker compose -f Codigo/docker-compose.yml --env-file .env up --build
   ```

3. Acesse:

   * Frontend: http://localhost:5173 (página inicial; o login da escola fica em `/login` e o do super admin em `/login/admin`)
   * Backend (health check): http://localhost:8080/health
   * Documentação da API (Swagger UI): http://localhost:8080/swagger-ui.html. Para testar uma rota protegida, chame `POST /login` (e `POST /login/codigo`, se a verificação em duas etapas estiver ligada), copie o `accessToken` e cole no botão **Authorize**. O contrato em JSON fica em http://localhost:8080/v3/api-docs. Só existe fora de produção.
   * Postgres: `localhost:5432` (credenciais definidas em `.env`)
   * pgAdmin: http://localhost:5050 (login com `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` do `.env`). O servidor **eduka (docker)** já aparece cadastrado; na primeira vez que abrir, informe a senha `POSTGRES_PASSWORD` do `.env`. Só existe no ambiente de dev.

   Os dados mockados (`Codigo/postgres/mock/mock.sql`) são carregados automaticamente pelo serviço `mock`, que espera o backend aplicar as migrations, roda o script e encerra. O super admin, por sua vez, é criado pelo `DevDataSeeder`, um `ApplicationRunner` do backend que roda automaticamente quando o perfil `dev` está ativo (padrão deste `docker-compose.yml`). Login do super admin: `admin@eduka.com.br` / `Eduka-admin123`. O seeder também cria a "Escola Dev" com um usuário de cada perfil de escola (`master`, `secretaria`, `financeiro`, `professor` e `aluno`, todos `@dev.eduka.com.br`, senha `Eduka-dev123`), e o `DevAcademicoSeeder` monta nela uma estrutura acadêmica de exemplo (período letivo, cursos, disciplinas e turmas com grade) quando a escola ainda não tem turma. Para entrar com qualquer um deles em um clique, abra http://localhost:5173/login/dev. Acompanhe com `docker compose -f Codigo/docker-compose.yml logs backend`. Só existe no ambiente de dev.

Os containers do backend e do frontend copiam o código na hora do build. Depois de alterar o código, reconstrua o serviço correspondente, por exemplo: `docker compose -f Codigo/docker-compose.yml --env-file .env up -d --build frontend`.

Para parar os serviços, use `docker compose -f Codigo/docker-compose.yml down` (adicione `-v` para também apagar os volumes do banco e do pgAdmin).

### Executando localmente sem Docker

**Backend** (Java 21 + Maven Wrapper), a partir de `Codigo/backend/`:

```bash
./mvnw spring-boot:run
```

O backend espera um Postgres em `localhost:5432` com as credenciais definidas em `application.properties` (ou nas variáveis `POSTGRES_DB`, `DB_APP_USER`, `DB_APP_PASSWORD`), além de `JWT_SECRET` e `PII_ENCRYPTION_KEY` no ambiente. O usuário `POSTGRES_USER` (definido em `.env`) é só o superuser de bootstrap do container Postgres — o backend conecta com o role restrito `DB_APP_USER`, criado automaticamente na primeira subida do container via `Codigo/postgres/init/`. Você pode subir só o banco via Docker com `docker compose -f Codigo/docker-compose.yml --env-file .env up postgres`.

**Frontend** (Node 20+), a partir de `Codigo/frontend/`:

```bash
npm install
npm run dev
```

### Autenticação e senhas

O backend é uma API stateless autenticada por JWT (HS256). Pontos que afetam quem for rodar ou consumir a API:

* **`JWT_SECRET` é obrigatório** e precisa ter no mínimo 32 bytes; o backend se recusa a subir sem ele. Gere um com `openssl rand -base64 48` e coloque em `.env`. A duração dos tokens é configurável por `JWT_ACCESS_EXPIRATION_MINUTES` (padrão 60) e `JWT_REFRESH_EXPIRATION_DAYS` (padrão 30).
* **Rotas protegidas por padrão.** Só são públicas `GET /health`, `POST /login`, `POST /login/codigo`, `POST /login/codigo/reenvio`, `POST /primeiro-acesso` e, fora de produção, o Swagger UI; qualquer outra rota exige o header `Authorization: Bearer <access token>` e responde `401` sem ele. `POST /login` recebe `{login, senha}` e devolve o access token e o refresh token. Com a verificação em duas etapas ligada (padrão; `LOGIN_VERIFICACAO_HABILITADA`), a senha certa devolve só uma `verificacao` pendente: o backend manda um código de 6 dígitos por e-mail e os tokens saem em `POST /login/codigo`. Em dev, sem `BREVO_API_KEY`, o código aparece no log do backend (`docker compose -f Codigo/docker-compose.yml logs backend`), e o login de um clique em `/login/dev` só funciona com `LOGIN_VERIFICACAO_HABILITADA=false` no `.env`. Só o *access* token abre rota protegida — o refresh token, mesmo assinado com a mesma chave, é recusado, e ainda não há endpoint de renovação.
* **Autorização por perfil.** O perfil do token vira `ROLE_<PERFIL>`. `RotasPorPerfil` define quais perfis entram em cada módulo da API (por exemplo, `/escolas` só para `SUPER_ADMIN`; `/alunos` para `MASTER`, `SECRETARIA`, `FINANCEIRO` e `PROFESSOR`), e os controllers refinam por ação com `@PreAuthorize` usando as constantes de `Perfis`. Perfil sem permissão recebe `403`.
* **Isolamento entre escolas.** A escola do usuário vem do token, nunca de parâmetro da requisição. Serviços filtram por `IsolamentoEscola.escolaIdAtual()` e chamam `exigirAcesso(...)` antes de devolver um recurso; entidades marcadas com `@TenantId` recebem o filtro por escola automaticamente do Hibernate. `SUPER_ADMIN` administra a plataforma (escolas e seus usuários) e não entra nos dados de negócio de nenhuma escola.
* **Dados pessoais cifrados.** Nome, CPF, data de nascimento, e-mail, telefone e endereço das pessoas (tabela `PESSOA`) são cifrados com AES-256-GCM pela aplicação antes de chegar ao banco, com a chave `PII_ENCRYPTION_KEY`, que o Postgres nunca vê.
* **CORS** restrito às origens em `CORS_ALLOWED_ORIGINS` (em dev, o Vite em `http://localhost:5173`; em prod, vazio, porque o Nginx serve front e API na mesma origem).
* **Erros em formato único** (`application/problem+json`, RFC 9457): `401`, `403`, `404`, `409`, `400` e `500` têm o mesmo corpo `{status, title, detail}`. Erro interno devolve só "Erro interno"; o detalhe fica no log.
* **Senhas nunca são gravadas em texto puro.** A coluna `usuarios.senha_hash` recebe um hash BCrypt de custo 12 com prefixo de algoritmo (`{bcrypt}$2a$12$...`). O prefixo permite trocar o algoritmo no futuro sem invalidar senhas existentes: hashes antigos continuam válidos e são regravados no formato atual no próximo login bem-sucedido.
* **Regras de senha:** mínimo de 8 caracteres e máximo de 72 bytes (limite do BCrypt, que ignora silenciosamente o que passa disso). Logins são normalizados (`trim` + minúsculas) antes de qualquer comparação.
* Login inexistente, senha errada e usuário inativo respondem com a mesma mensagem, para não revelar quais logins existem.

### Executando os testes

Backend, a partir de `Codigo/backend/`:

```bash
./mvnw test
```

Os testes unitários e de segurança (encoder, serviços, filter chain) trocam os repositórios por mock e não precisam de banco. Os testes de integração (`BackendApplicationTests` e os do pacote `integracao`) rodam contra um **Postgres de verdade**, que o Testcontainers sobe num container descartável: precisam só do **Docker ligado**, não do banco do `docker-compose`. Sem Docker, esses testes são pulados e os demais continuam rodando.

Frontend (testes de tela com Vitest e Testing Library, sem navegador nem backend), a partir de `Codigo/frontend/`:

```bash
npm test
```

### Executando em produção

O ambiente de produção usa um único ponto de entrada (Nginx na porta 80), que serve o frontend compilado como estático e faz proxy das chamadas de API para o backend.

1. Configure as variáveis de ambiente em `.env` (as mesmas do ambiente de desenvolvimento: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_APP_USER`, `DB_APP_PASSWORD`, `DB_SSLMODE`, `JWT_SECRET`, `PII_ENCRYPTION_KEY`). **Troque todos os valores `changeme`** — em produção nenhuma credencial tem default, então uma variável faltando derruba o backend na subida com `Could not resolve placeholder` em vez de subir com senha conhecida.

2. Suba os serviços com o compose de produção:

   ```bash
   docker compose -f Codigo/docker-compose.prod.yml --env-file .env up --build -d
   ```

3. Acesse:

   * Aplicação (frontend): http://localhost/
   * Backend, via proxy do Nginx: http://localhost/api/... (ex.: http://localhost/api/health)

Diferenças em relação ao ambiente de desenvolvimento:

* Apenas a porta 80 (Nginx) fica exposta ao host; frontend e backend não publicam portas diretamente.
* O backend roda com `SPRING_PROFILES_ACTIVE=prod`, que carrega `application-prod.properties` por cima da configuração padrão: credenciais sem default, log de SQL desligado e respostas de erro sem stack trace, mensagem interna ou nome de exceção.
* O frontend é compilado (`npm run build`) e servido como arquivos estáticos pelo próprio Nginx (`Codigo/frontend/Dockerfile.prod`).
* Os serviços têm `restart: always`.

Para parar os serviços, use `docker compose -f Codigo/docker-compose.prod.yml down` (adicione `-v` para também apagar o volume do banco de dados).

