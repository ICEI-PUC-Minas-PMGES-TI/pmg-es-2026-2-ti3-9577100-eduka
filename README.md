# [Nome do projeto]

[Escreva um ou dois  parágrafo resumindo o objetivo do seu projeto.]

## Alunos integrantes da equipe

* [Nome completo do aluno 1]
* [Nome completo do aluno 2]
* [Nome completo do aluno 3]
* [Nome completo do aluno 4]

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
   docker compose up --build
   ```

3. Acesse:

   * Frontend: http://localhost:5173
   * Backend (health check): http://localhost:8080/health
   * Postgres: `localhost:5432` (credenciais definidas em `.env`)

Para parar os serviços, use `docker compose down` (adicione `-v` para também apagar o volume do banco de dados).

### Executando localmente sem Docker

**Backend** (Java 21 + Maven Wrapper), a partir de `backend/`:

```bash
./mvnw spring-boot:run
```

O backend espera um Postgres em `localhost:5432` com as credenciais definidas em `application.properties` (ou nas variáveis `POSTGRES_DB`, `DB_APP_USER`, `DB_APP_PASSWORD`). O usuário `POSTGRES_USER` (definido em `.env`) é só o superuser de bootstrap do container Postgres — o backend conecta com o role restrito `DB_APP_USER`, criado automaticamente na primeira subida do container via `postgres/init/`. Você pode subir só o banco via Docker com `docker compose up postgres`.

**Frontend** (Node 20+), a partir de `frontend/`:

```bash
npm install
npm run dev
```

### Executando em produção

O ambiente de produção usa um único ponto de entrada (Nginx na porta 80), que serve o frontend compilado como estático e faz proxy das chamadas de API para o backend.

1. Configure as variáveis de ambiente em `.env` (as mesmas do ambiente de desenvolvimento: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_APP_USER`, `DB_APP_PASSWORD`).

2. Suba os serviços com o compose de produção:

   ```bash
   docker compose -f docker-compose.prod.yml up --build -d
   ```

3. Acesse:

   * Aplicação (frontend): http://localhost/
   * Backend, via proxy do Nginx: http://localhost/api/... (ex.: http://localhost/api/health)

Diferenças em relação ao ambiente de desenvolvimento:

* Apenas a porta 80 (Nginx) fica exposta ao host; frontend e backend não publicam portas diretamente.
* O backend roda com `SPRING_PROFILES_ACTIVE=prod`.
* O frontend é compilado (`npm run build`) e servido como arquivos estáticos pelo próprio Nginx (`frontend/Dockerfile.prod`).
* Os serviços têm `restart: always`.

Para parar os serviços, use `docker compose -f docker-compose.prod.yml down` (adicione `-v` para também apagar o volume do banco de dados).

