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

O backend espera um Postgres em `localhost:5432` com as credenciais definidas em `application.properties` (ou nas variáveis `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`). Você pode subir só o banco via Docker com `docker compose up postgres`.

**Frontend** (Node 20+), a partir de `frontend/`:

```bash
npm install
npm run dev
```
