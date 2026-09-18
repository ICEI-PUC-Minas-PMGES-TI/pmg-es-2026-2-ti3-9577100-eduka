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

* [Nome completo do professor 1]
* [Nome completo do professor 2]

## Instruções de utilização

### Executando com Docker (recomendado)

1. Copie o arquivo de variáveis de ambiente de exemplo:

   ```bash
   cp .env.example .env
   ```

2. Suba os serviços (Postgres, backend e frontend):

   ```bash
   docker compose -f Codigo/docker-compose.yml --env-file .env up --build
   ```

3. Acesse:

   * Frontend: http://localhost:5173
   * Backend (health check): http://localhost:8080/health
   * Postgres: `localhost:5432` (credenciais definidas em `.env`)

Para parar os serviços, use `docker compose -f Codigo/docker-compose.yml down` (adicione `-v` para também apagar o volume do banco de dados).

### Executando localmente sem Docker

**Backend** (Java 21 + Maven Wrapper), a partir de `Codigo/backend/`:

```bash
./mvnw spring-boot:run
```

O backend espera um Postgres em `localhost:5432` com as credenciais definidas em `application.properties` (ou nas variáveis `POSTGRES_DB`, `DB_APP_USER`, `DB_APP_PASSWORD`). O usuário `POSTGRES_USER` (definido em `.env`) é só o superuser de bootstrap do container Postgres — o backend conecta com o role restrito `DB_APP_USER`, criado automaticamente na primeira subida do container via `Codigo/postgres/init/`. Você pode subir só o banco via Docker com `docker compose -f Codigo/docker-compose.yml --env-file .env up postgres`.

**Frontend** (Node 20+), a partir de `Codigo/frontend/`:

```bash
npm install
npm run dev
```

### Autenticação e senhas

O backend é uma API stateless autenticada por JWT (HS256). Pontos que afetam quem for rodar ou consumir a API:

* **`JWT_SECRET` é obrigatório** e precisa ter no mínimo 32 bytes; o backend se recusa a subir sem ele. Gere um com `openssl rand -base64 48` e coloque em `.env`. A duração dos tokens é configurável por `JWT_ACCESS_EXPIRATION_MINUTES` (padrão 60) e `JWT_REFRESH_EXPIRATION_DAYS` (padrão 30).
* **Rotas protegidas por padrão.** Apenas `GET /health` e `/auth/**` são públicas; qualquer outra rota exige o header `Authorization: Bearer <access token>` e responde `401` sem ele. Os endpoints HTTP de login e cadastro ainda não existem — só os serviços internos (`AutenticacaoService`, `UsuarioService`).
* **Senhas nunca são gravadas em texto puro.** A coluna `USUARIO.SENHA_HASH` recebe um hash BCrypt de custo 12 com prefixo de algoritmo (`{bcrypt}$2a$12$...`). O prefixo permite trocar o algoritmo no futuro sem invalidar senhas existentes: hashes antigos continuam válidos e são regravados no formato atual no próximo login bem-sucedido.
* **Regras de senha:** mínimo de 8 caracteres e máximo de 72 bytes (limite do BCrypt, que ignora silenciosamente o que passa disso). Logins são normalizados (`trim` + minúsculas) antes de qualquer comparação.
* Login inexistente, senha errada e usuário inativo respondem com a mesma mensagem, para não revelar quais logins existem.

### Executando os testes

A partir de `Codigo/backend/`:

```bash
./mvnw test
```

Os testes unitários e de segurança (encoder, serviços de cadastro/login, filter chain) não precisam de banco. Apenas `BackendApplicationTests.contextLoads` sobe o contexto completo e exige um Postgres acessível em `localhost:5432` — sem ele, esse teste falha com `Connection refused` e os demais continuam passando. Para rodá-lo, suba só o banco com `docker compose -f Codigo/docker-compose.yml --env-file .env up postgres`.

### Executando em produção

O ambiente de produção usa um único ponto de entrada (Nginx na porta 80), que serve o frontend compilado como estático e faz proxy das chamadas de API para o backend.

1. Configure as variáveis de ambiente em `.env` (as mesmas do ambiente de desenvolvimento: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_APP_USER`, `DB_APP_PASSWORD`, `DB_SSLMODE`, `JWT_SECRET`). **Troque todos os valores `changeme`** — em produção nenhuma credencial tem default, então uma variável faltando derruba o backend na subida com `Could not resolve placeholder` em vez de subir com senha conhecida.

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

