# Estrutura de perfis de usuário — design

Branch: `db-perfis-de-uso`

## Contexto

O backend (`backend/`) é um projeto Spring Boot novo: sem entidades JPA além do
health check, sem ferramenta de migration (Hibernate está em
`ddl-auto=update`), e sem nenhuma tabela criada. O `docker-compose.yml` /
`docker-compose.prod.yml` sobem um único usuário Postgres (`eduka`), que é o
usuário de bootstrap criado pela imagem oficial — portanto é **superuser da
instância inteira**, não apenas dono do banco `eduka`.

O pedido original era "cadastrar no banco de dados a estrutura de perfis
(SUPER_ADMIN, MASTER, SECRETARIA, FINANCEIRO, PROFESSOR, ALUNO)". Durante o
brainstorming, o escopo cresceu para incluir também proteção de dados
(criptografia de PII em `PESSOA`) e restrição de privilégios de banco, que o
usuário pediu para deixar "meio pronto" já no início desta branch.

## Decisões

Cada decisão abaixo foi validada explicitamente com o usuário:

1. **Escopo**: perfis + cadeia mínima de tabelas (`EMPRESA`, `PESSOA`,
   `PESSOA_PAPEL`, `USUARIO`) — não isolado, para o perfil já ser utilizável.
2. **Migrations**: adotar Flyway (SQL versionado), abandonando
   `ddl-auto=update` em favor de `validate`.
3. **Representação do perfil**: `CHECK` constraint em `USUARIO.PERFIL` (estilo
   do rascunho original do usuário), não enum nativo nem tabela de cadastro
   separada.
4. **Schema/banco**: usar o banco/schema atuais do docker-compose (`eduka`,
   schema `public`) — não replicar as notas antigas de Supabase
   (`APP_SAAS`, `uuid-ossp`). `gen_random_uuid()` é nativo no Postgres 16.
5. **RLS multi-tenant**: adiado para uma tarefa futura dedicada (precisa de
   código Spring setando a variável de sessão por request; sem isso, RLS é
   decorativo ou — pior — bloqueia todo acesso).
6. **Entidades JPA**: fora de escopo. Esta tarefa é só a migration SQL.
7. **`USUARIO.LOGIN`**: `UNIQUE` global.
8. **Restrição de privilégio de banco**: criar um role `eduka_app`
   (não-superuser) que o backend passa a usar; `eduka` vira só bootstrap.
9. **Criptografia de colunas sensíveis**: `pgcrypto`, aplicada a **todas** as
   colunas de PII de `PESSOA` (`NOME`, `DATA_NASCIMENTO`, `CPF`, `EMAIL`,
   `TELEFONE`), para todas as pessoas (não só ALUNO) — decisão consciente de
   que isso inviabiliza `ORDER BY`/`LIKE`/filtro por idade direto no banco até
   uma solução de busca (ex: coluna de índice cego) ser desenhada depois.
10. **`PESSOA_PAPEL`**: incluída nesta migration (mesmo sem diferenciação de
    cifra por papel ainda) para já existir a base de "quem é ALUNO" para uma
    tarefa futura de refinamento da cifra por papel.
11. **TLS na conexão backend→Postgres**: só preparar um parâmetro
    configurável (`sslmode`) e documentar; não gerar certificado nem forçar
    SSL agora (nginx do prod nem tem HTTPS ainda — é outra tarefa).

## Modelo de dados desta migration

```sql
-- extensão para cifra de colunas sensíveis
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE EMPRESA (
    ID_EMPRESA   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    NOME         VARCHAR(150) NOT NULL,
    CNPJ_CPF     VARCHAR(20),
    ENDERECO     VARCHAR(200),
    EMAIL        VARCHAR(150),
    TELEFONE     VARCHAR(20),
    ATIVO        BOOLEAN NOT NULL DEFAULT TRUE,
    CRIADO_EM    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE PESSOA (
    ID_PESSOA         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    EMPRESA_ID        UUID NOT NULL REFERENCES EMPRESA(ID_EMPRESA),
    NOME              BYTEA NOT NULL,           -- pgp_sym_encrypt(nome, chave)
    DATA_NASCIMENTO   BYTEA,                    -- pgp_sym_encrypt(data::text, chave)
    CPF               BYTEA,                    -- pgp_sym_encrypt(cpf, chave)
    EMAIL             BYTEA,                    -- pgp_sym_encrypt(email, chave)
    TELEFONE          BYTEA,                    -- pgp_sym_encrypt(telefone, chave)
    ATIVO             BOOLEAN NOT NULL DEFAULT TRUE,
    CRIADO_EM         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE PESSOA_PAPEL (
    ID_PESSOA_PAPEL   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    EMPRESA_ID        UUID NOT NULL REFERENCES EMPRESA(ID_EMPRESA),
    PESSOA_ID         UUID NOT NULL REFERENCES PESSOA(ID_PESSOA),
    PAPEL             TEXT NOT NULL CHECK (PAPEL IN
                         ('ALUNO','RESPONSAVEL','PROFESSOR','FUNCIONARIO','USUARIO')),
    CRIADO_EM         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (PESSOA_ID, PAPEL)
);

CREATE TABLE USUARIO (
    ID_USUARIO    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    EMPRESA_ID    UUID NOT NULL REFERENCES EMPRESA(ID_EMPRESA),
    PESSOA_ID     UUID NOT NULL REFERENCES PESSOA(ID_PESSOA),
    LOGIN         VARCHAR(150) NOT NULL UNIQUE,
    SENHA_HASH    TEXT NOT NULL,
    PERFIL        TEXT NOT NULL CHECK (PERFIL IN
                     ('SUPER_ADMIN','MASTER','SECRETARIA','FINANCEIRO','PROFESSOR','ALUNO')),
    ATIVO         BOOLEAN NOT NULL DEFAULT TRUE,
    CRIADO_EM     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Notas:
- `SENHA_HASH` continua um hash de aplicação (bcrypt/argon2) em texto — não é
  "criptografia" no sentido de pgcrypto, é hash de mão única; nenhuma mudança
  necessária aqui.
- A chave usada em `pgp_sym_encrypt` **não** é definida nesta migration (não
  há código de aplicação ainda gravando em `PESSOA`). Fica reservado o nome de
  env var `APP_PGCRYPTO_KEY` em `.env.example`, para quando uma tarefa futura
  implementar a gravação/leitura via JPA.

## Infraestrutura

**Flyway**
- `backend/pom.xml`: adicionar `org.flywaydb:flyway-database-postgresql`.
- `backend/src/main/resources/application.properties`:
  `spring.jpa.hibernate.ddl-auto=validate`.
- Migration em `backend/src/main/resources/db/migration/V1__criar_estrutura_perfis.sql`.

**Role de banco restrito**
- Novo `postgres/init/01-create-app-role.sql` (script `.sh` que roda via
  `psql`, usando env vars), montado em `docker-entrypoint-initdb.d/` nos dois
  `docker-compose*.yml`. Cria o role `eduka_app` (login, sem superuser),
  torna-o dono do schema `public` do banco `eduka`.
- Novas env vars `DB_APP_USER` / `DB_APP_PASSWORD` em `.env.example` e `.env`.
- `docker-compose.yml` e `docker-compose.prod.yml`: `SPRING_DATASOURCE_USERNAME`
  / `SPRING_DATASOURCE_PASSWORD` do serviço `backend` passam a apontar para
  `DB_APP_USER` / `DB_APP_PASSWORD` em vez de `POSTGRES_USER` / `POSTGRES_PASSWORD`.
- `application.properties`: mesma troca nos defaults locais
  (`spring.datasource.username=${DB_APP_USER:eduka_app}` etc.), mantendo
  `POSTGRES_USER`/`POSTGRES_PASSWORD` só para o bootstrap do container Postgres.

**TLS (preparo, não ativação)**
- `application.properties`: acrescentar `sslmode` configurável na URL JDBC via
  env var (ex: `${DB_SSLMODE:disable}`), sem alterar o comportamento padrão
  atual.
- Comentário/nota (não um documento novo) apontando que, em produção, deve-se
  gerar certificado e trocar para `verify-full` — tarefa futura.

## Fora de escopo (explicitamente adiado)

- RLS multi-tenant real (`CURRENT_SETTING('empresa_id')` + `FORCE ROW LEVEL
  SECURITY` + middleware Spring).
- Demais tabelas do domínio (`CURSO`, `TURMA`, `MATRICULA`, `COBRANCA`,
  `PAGAMENTO`, `ALUNO_RESPONSAVEL`, etc.).
- Entidades JPA / repositórios / endpoints para `EMPRESA`, `PESSOA`,
  `PESSOA_PAPEL`, `USUARIO`.
- Ativação de fato do TLS (geração de certificado, `verify-full`).
- Estratégia de busca sobre campos cifrados (índice cego, coluna auxiliar,
  etc.) — necessária antes de haver telas de listagem de alunos.
- Diferenciação de cifra por papel (hoje todas as `PESSOA` são cifradas por
  igual, independente de `PESSOA_PAPEL`).

## Verificação

- `docker compose up` local: Postgres sobe, script de init cria `eduka_app`;
  backend sobe conectando como `eduka_app` e o Flyway aplica `V1` com sucesso
  (log `Successfully applied 1 migration`).
- `psql` como `eduka_app`: `\d usuario` mostra o `CHECK` com os 6 perfis e o
  `UNIQUE` em `login`; `\d pessoa` mostra as colunas de PII como `bytea`.
- Confirmar que `eduka_app` **não** consegue `CREATE DATABASE` nem
  `CREATE ROLE` (prova de que não é superuser), mas consegue `CREATE TABLE`
  no schema `public` do banco `eduka` (prova de que o Flyway funciona).
- Sanity check do `pgcrypto` via `psql`: `SELECT pgp_sym_decrypt(pgp_sym_encrypt('teste', 'chave-temporaria'), 'chave-temporaria');` deve retornar `'teste'`, confirmando que a extensão está operante (sem depender de código Java).
