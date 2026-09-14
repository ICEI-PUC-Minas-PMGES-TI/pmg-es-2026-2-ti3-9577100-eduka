# Estrutura de Perfis de Usuário Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar no banco Postgres a estrutura mínima de `EMPRESA`/`PESSOA`/`PESSOA_PAPEL`/`USUARIO` com perfis de acesso (`SUPER_ADMIN`, `MASTER`, `SECRETARIA`, `FINANCEIRO`, `PROFESSOR`, `ALUNO`), controlada por Flyway, rodando sob um role de banco não-superuser, com os campos de PII de `PESSOA` já cifrados via `pgcrypto`.

**Architecture:** Uma única migration Flyway (`V1__criar_estrutura_perfis.sql`) cria as 4 tabelas no schema `public` do banco `eduka` já existente. Um script de bootstrap do Postgres (`postgres/init/`) cria um role `eduka_app` (dono do banco, sem superuser) que passa a ser usado pelo backend em vez do usuário `eduka` (que é superuser da instância, criado pela imagem oficial). Nenhuma entidade JPA é criada nesta fase — é só schema.

**Tech Stack:** Spring Boot (parent 4.1.1) + Flyway (`flyway-database-postgresql`) + Postgres 16 (`pgcrypto` nativo) + Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-14-estrutura-perfis-design.md`

## Global Constraints

- Postgres pinned em `postgres:16-alpine` nos dois composes (dev e prod) — não trocar de imagem.
- Java 21, Spring Boot parent `4.1.1` — não alterar versões.
- Um único datasource: o mesmo role (`eduka_app`) roda tanto o Flyway (DDL) quanto o JPA (DML) — sem datasource separado para migrations.
- RLS multi-tenant e entidades JPA ficam fora desta fase (adiados explicitamente no spec).
- `.env` nunca é commitado (já está no `.gitignore`); segredos reais só existem em `.env` local, `.env.example` só tem placeholders.
- Commits: Conventional Commits (`tipo(escopo): descrição`), `--no-gpg-sign`, sem trailer `Co-Authored-By`.
- Schema/tabelas no schema `public` do banco `eduka` atual — não criar schema `APP_SAAS` nem extension `uuid-ossp` (não usada; `gen_random_uuid()` é nativo do Postgres 16).

---

### Task 1: Migration Flyway com EMPRESA, PESSOA, PESSOA_PAPEL e USUARIO

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/resources/db/migration/V1__criar_estrutura_perfis.sql`

**Interfaces:**
- Produces: tabelas `empresa(id_empresa, nome, cnpj_cpf, endereco, email, telefone, ativo, criado_em)`, `pessoa(id_pessoa, empresa_id, nome, data_nascimento, cpf, email, telefone, ativo, criado_em)` (as 5 últimas colunas de PII em `bytea`, cifradas), `pessoa_papel(id_pessoa_papel, empresa_id, pessoa_id, papel, criado_em)`, `usuario(id_usuario, empresa_id, pessoa_id, login, senha_hash, perfil, ativo, criado_em)`. `usuario.perfil` aceita apenas `'SUPER_ADMIN','MASTER','SECRETARIA','FINANCEIRO','PROFESSOR','ALUNO'`. `usuario.login` é `UNIQUE`. Extension `pgcrypto` fica habilitada no banco a partir daqui — Task 2 depende disso continuar funcionando sob o novo role.

- [ ] **Step 1: Confirmar estado atual (baseline antes da mudança)**

Rode a partir da raiz do repo:

```bash
docker compose up -d --build
docker compose logs backend | grep -i hibernate
```

Esperado: o backend sobe sem Flyway (ainda não existe no classpath) e o Hibernate está em modo `update` (nenhuma tabela é criada, pois não há entidades `@Entity`).

- [ ] **Step 2: Adicionar a dependência do Flyway**

Em `backend/pom.xml`, dentro de `<dependencies>`, logo após a dependência do `postgresql`:

```xml
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>
```

- [ ] **Step 3: Trocar `ddl-auto` para `validate`**

Em `backend/src/main/resources/application.properties`, troque:

```properties
spring.jpa.hibernate.ddl-auto=update
```

por:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

- [ ] **Step 4: Criar a migration**

Crie `backend/src/main/resources/db/migration/V1__criar_estrutura_perfis.sql`:

```sql
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
    NOME              BYTEA NOT NULL,
    DATA_NASCIMENTO   BYTEA,
    CPF               BYTEA,
    EMAIL             BYTEA,
    TELEFONE          BYTEA,
    ATIVO             BOOLEAN NOT NULL DEFAULT TRUE,
    CRIADO_EM         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE PESSOA_PAPEL (
    ID_PESSOA_PAPEL   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    EMPRESA_ID        UUID NOT NULL REFERENCES EMPRESA(ID_EMPRESA),
    PESSOA_ID         UUID NOT NULL REFERENCES PESSOA(ID_PESSOA),
    PAPEL             TEXT NOT NULL CHECK (PAPEL IN
                         ('ALUNO', 'RESPONSAVEL', 'PROFESSOR', 'FUNCIONARIO', 'USUARIO')),
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
                     ('SUPER_ADMIN', 'MASTER', 'SECRETARIA', 'FINANCEIRO', 'PROFESSOR', 'ALUNO')),
    ATIVO         BOOLEAN NOT NULL DEFAULT TRUE,
    CRIADO_EM     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 5: Subir o stack e verificar que a migration aplicou**

```bash
docker compose up -d --build
docker compose logs backend | grep -i flyway
```

Esperado: uma linha como `Successfully applied 1 migration to schema "public"`.

- [ ] **Step 6: Verificar a estrutura das tabelas via psql**

```bash
docker compose exec postgres psql -U eduka -d eduka -c '\d usuario'
docker compose exec postgres psql -U eduka -d eduka -c '\d pessoa'
```

Esperado: `usuario` mostra o `CHECK` com os 6 valores de perfil e `login` com constraint `UNIQUE`; `pessoa` mostra `nome`, `data_nascimento`, `cpf`, `email`, `telefone` como tipo `bytea`.

- [ ] **Step 7: Sanity check do pgcrypto**

```bash
docker compose exec postgres psql -U eduka -d eduka -c "SELECT pgp_sym_decrypt(pgp_sym_encrypt('teste', 'chave-temporaria'), 'chave-temporaria');"
```

Esperado: retorna `teste`.

- [ ] **Step 8: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.properties backend/src/main/resources/db/migration/V1__criar_estrutura_perfis.sql
git commit --no-gpg-sign -m "feat(backend): cria estrutura de perfis via migration flyway

- adiciona flyway-database-postgresql e troca ddl-auto para validate
- cria empresa, pessoa, pessoa_papel e usuario com perfis de acesso
- cifra colunas de PII de pessoa com pgcrypto"
```

---

### Task 2: Role de banco restrito (`eduka_app`) substituindo o superuser

**Files:**
- Create: `postgres/init/01-create-app-role.sh`
- Modify: `docker-compose.yml`
- Modify: `docker-compose.prod.yml`
- Modify: `.env`
- Modify: `.env.example`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**
- Consumes: banco `eduka` e role `eduka` (superuser de bootstrap) já existentes via `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` (Task anterior/infra atual).
- Produces: role `eduka_app` (login, dono do banco `eduka`, não-superuser), variáveis de ambiente `DB_APP_USER`/`DB_APP_PASSWORD` consumidas pelo `SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` em ambos os composes e por `application.properties`.

- [ ] **Step 1: Confirmar que hoje o backend conecta como superuser**

```bash
docker compose exec postgres psql -U eduka -d eduka -c "SELECT rolname, rolsuper FROM pg_roles WHERE rolname = current_user;"
```

Esperado: `rolsuper = t` (confirma o problema que este task resolve).

- [ ] **Step 2: Criar o script de bootstrap do role restrito**

Crie `postgres/init/01-create-app-role.sh`:

```sh
#!/bin/bash
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE "${DB_APP_USER}" LOGIN PASSWORD '${DB_APP_PASSWORD}';
    ALTER DATABASE "${POSTGRES_DB}" OWNER TO "${DB_APP_USER}";
    GRANT ALL PRIVILEGES ON DATABASE "${POSTGRES_DB}" TO "${DB_APP_USER}";
EOSQL
```

Torne executável:

```bash
chmod +x postgres/init/01-create-app-role.sh
```

- [ ] **Step 3: Adicionar as novas variáveis de ambiente**

Em `.env.example`, acrescente ao final:

```
DB_APP_USER=eduka_app
DB_APP_PASSWORD=changeme-app
```

Em `.env` (arquivo local, não commitado), acrescente as mesmas duas linhas.

- [ ] **Step 4: Montar o script de init e propagar as novas env vars no `docker-compose.yml`**

Em `docker-compose.yml`, no serviço `postgres`, troque:

```yaml
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}

    ports:
      - "${POSTGRES_PORT}:5432"

    volumes:
      - postgres_data:/var/lib/postgresql/data
```

por:

```yaml
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      DB_APP_USER: ${DB_APP_USER}
      DB_APP_PASSWORD: ${DB_APP_PASSWORD}

    ports:
      - "${POSTGRES_PORT}:5432"

    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d
```

E no serviço `backend`, troque:

```yaml
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
```

por:

```yaml
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${DB_APP_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_APP_PASSWORD}
```

- [ ] **Step 5: Mesmas mudanças no `docker-compose.prod.yml`**

No serviço `postgres`, troque:

```yaml
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}

    volumes:
      - postgres_data:/var/lib/postgresql/data
```

por:

```yaml
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      DB_APP_USER: ${DB_APP_USER}
      DB_APP_PASSWORD: ${DB_APP_PASSWORD}

    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d
```

No serviço `backend`, troque:

```yaml
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}

      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
```

por:

```yaml
      SPRING_DATASOURCE_USERNAME: ${DB_APP_USER}

      SPRING_DATASOURCE_PASSWORD: ${DB_APP_PASSWORD}
```

- [ ] **Step 6: Atualizar `application.properties` para usar o novo role por padrão**

Troque:

```properties
spring.datasource.username=${POSTGRES_USER:eduka}
spring.datasource.password=${POSTGRES_PASSWORD:changeme}
```

por:

```properties
spring.datasource.username=${DB_APP_USER:eduka_app}
spring.datasource.password=${DB_APP_PASSWORD:changeme-app}
```

- [ ] **Step 7: Recriar o ambiente do zero e verificar**

O script de init só roda em um volume novo — por isso é preciso derrubar o volume atual (ambiente local, sem dados reais):

```bash
docker compose down -v
docker compose up -d --build
docker compose logs postgres | grep -i "CREATE ROLE\|ERROR"
docker compose logs backend | grep -i flyway
curl -s http://localhost:8080/health
```

Esperado: sem erros no log do postgres, Flyway aplica a migration novamente (agora como `eduka_app`), e o health check responde OK.

- [ ] **Step 8: Confirmar que `eduka_app` não é superuser mas consegue usar o schema**

```bash
docker compose exec postgres psql -U eduka_app -d eduka -c "CREATE DATABASE nao_deveria_funcionar;"
docker compose exec postgres psql -U eduka_app -d eduka -c '\d usuario'
```

Esperado: o primeiro comando falha com erro de permissão (`permission denied to create database`); o segundo funciona normalmente, mostrando a tabela.

- [ ] **Step 9: Commit**

```bash
git add postgres/init/01-create-app-role.sh docker-compose.yml docker-compose.prod.yml .env.example backend/src/main/resources/application.properties
git commit --no-gpg-sign -m "feat(infra): restringe conexao do backend a um role de banco nao-superuser

- cria role eduka_app (dono do banco eduka, sem privilegios de instancia)
- backend passa a conectar via eduka_app em dev e producao
- eduka (usuario de bootstrap da imagem postgres) fica restrito ao init do container"
```

(O `.env` local não entra no commit — está no `.gitignore`.)

---

### Task 3: Preparo de TLS na conexão e documentação

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Modify: `.env.example`
- Modify: `.env`
- Modify: `README.md`

**Interfaces:**
- Produces: parâmetro `DB_SSLMODE` (default `disable`, não muda comportamento atual) usado na URL JDBC; variável `APP_PGCRYPTO_KEY` reservada (comentada) em `.env.example` para uso por código futuro que grave/leia `PESSOA`.

- [ ] **Step 1: Adicionar `sslmode` configurável na URL JDBC**

Em `backend/src/main/resources/application.properties`, troque:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/${POSTGRES_DB:eduka}
```

por:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/${POSTGRES_DB:eduka}?sslmode=${DB_SSLMODE:disable}
```

- [ ] **Step 2: Reservar as variáveis em `.env.example`**

Acrescente ao final de `.env.example`:

```
DB_SSLMODE=disable

# Reservado para uso futuro: chave simetrica usada por pgp_sym_encrypt/pgp_sym_decrypt
# ao gravar ou ler colunas sensiveis de PESSOA. Ainda nao consumida por nenhum codigo.
#APP_PGCRYPTO_KEY=
```

- [ ] **Step 3: Adicionar `DB_SSLMODE` em `.env`**

Acrescente ao final de `.env` (arquivo local):

```
DB_SSLMODE=disable
```

- [ ] **Step 4: Atualizar o README**

Em `README.md`, na seção "Executando localmente sem Docker", troque:

```
O backend espera um Postgres em `localhost:5432` com as credenciais definidas em `application.properties` (ou nas variáveis `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`). Você pode subir só o banco via Docker com `docker compose up postgres`.
```

por:

```
O backend espera um Postgres em `localhost:5432` com as credenciais definidas em `application.properties` (ou nas variáveis `POSTGRES_DB`, `DB_APP_USER`, `DB_APP_PASSWORD`). O usuário `POSTGRES_USER` (definido em `.env`) é só o superuser de bootstrap do container Postgres — o backend conecta com o role restrito `DB_APP_USER`, criado automaticamente na primeira subida do container via `postgres/init/`. Você pode subir só o banco via Docker com `docker compose up postgres`.
```

E, na seção "Executando em produção", troque:

```
1. Configure as variáveis de ambiente em `.env` (as mesmas do ambiente de desenvolvimento: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`).
```

por:

```
1. Configure as variáveis de ambiente em `.env` (as mesmas do ambiente de desenvolvimento: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_APP_USER`, `DB_APP_PASSWORD`).
```

- [ ] **Step 5: Verificar que o stack ainda sobe normalmente**

```bash
docker compose up -d --build
curl -s http://localhost:8080/health
docker compose logs backend | grep -i flyway
```

Esperado: health check OK, Flyway continua reportando a migration já aplicada (sem reaplicar).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/application.properties .env.example README.md
git commit --no-gpg-sign -m "docs(backend): documenta role restrito e prepara parametro de TLS

- adiciona sslmode configuravel na url jdbc (default mantem comportamento atual)
- reserva variavel de chave do pgcrypto para uso futuro
- atualiza README com as novas variaveis de ambiente"
```

(O `.env` local não entra no commit — está no `.gitignore`.)

---

## Verificação final (após as 3 tasks)

```bash
docker compose down -v
docker compose up -d --build
docker compose logs backend | grep -i flyway
docker compose exec postgres psql -U eduka_app -d eduka -c '\d usuario'
docker compose exec postgres psql -U eduka_app -d eduka -c '\d pessoa'
curl -s http://localhost:8080/health
```

Esperado: Flyway aplica `V1` com sucesso rodando como `eduka_app`, `usuario` mostra o `CHECK` com os 6 perfis e `UNIQUE` em `login`, `pessoa` mostra as colunas de PII como `bytea`, e o health check responde OK.
