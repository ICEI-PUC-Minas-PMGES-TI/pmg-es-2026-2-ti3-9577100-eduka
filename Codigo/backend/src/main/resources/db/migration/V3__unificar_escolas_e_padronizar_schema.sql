-- V1 criou EMPRESA (UUID) como tenant e V2 criou escolas (BIGINT) para o mesmo
-- conceito, sem que nada apontasse para ela. Esta migration:
--   1. mantem escolas como a unica tabela de tenant, com chave UUID e os campos
--      que so existiam em EMPRESA;
--   2. migra os dados de EMPRESA e da escolas antiga para a nova;
--   3. padroniza nomes em minusculo_plural (pessoas, pessoas_papeis, usuarios)
--      e a coluna de tenant em escola_id;
--   4. converte TIMESTAMP para TIMESTAMPTZ;
--   5. garante que usuario e papel apontem para uma pessoa da MESMA escola,
--      via chave estrangeira composta (pessoa_id, escola_id).


-- ---------------------------------------------------------------------------
-- 1. Nova tabela escolas
-- ---------------------------------------------------------------------------
ALTER TABLE escolas RENAME TO escolas_v2;

CREATE TABLE escolas (
    id_escola          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome               VARCHAR(150) NOT NULL,
    cnpj               VARCHAR(20),
    endereco           VARCHAR(200),
    email_responsavel  VARCHAR(150),
    telefone           VARCHAR(20),
    status             VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
                         CONSTRAINT escolas_status_check
                         CHECK (status IN ('ATIVO', 'INATIVO', 'BLOQUEADO')),
    criado_em          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em      TIMESTAMPTZ
);


-- ---------------------------------------------------------------------------
-- 2. Migracao de dados
-- ---------------------------------------------------------------------------
-- EMPRESA: preserva o UUID, porque pessoas/usuarios ja apontam para ele.
INSERT INTO escolas (id_escola, nome, cnpj, endereco, email_responsavel, telefone, status, criado_em)
SELECT id_empresa,
       nome,
       cnpj_cpf,
       endereco,
       email,
       telefone,
       CASE WHEN ativo THEN 'ATIVO' ELSE 'INATIVO' END,
       criado_em AT TIME ZONE 'UTC'
FROM empresa;

-- escolas (V2): nada referenciava o id BIGINT, entao recebe UUID novo.
-- A tabela antiga nao tinha nome; usa o CNPJ como fallback para nao violar NOT NULL.
INSERT INTO escolas (nome, cnpj, email_responsavel, telefone, status, criado_em, atualizado_em)
SELECT COALESCE(cnpj, 'Escola ' || id_escola),
       cnpj,
       email_responsavel,
       telefone,
       status,
       COALESCE(criado_em AT TIME ZONE 'UTC', CURRENT_TIMESTAMP),
       atualizado_em AT TIME ZONE 'UTC'
FROM escolas_v2;

DROP TABLE escolas_v2;


-- ---------------------------------------------------------------------------
-- 3. Renomeia tabelas, colunas e constraints para minusculo_plural
-- ---------------------------------------------------------------------------
ALTER TABLE pessoa       RENAME TO pessoas;
ALTER TABLE pessoa_papel RENAME TO pessoas_papeis;
ALTER TABLE usuario      RENAME TO usuarios;

ALTER TABLE pessoas        RENAME COLUMN empresa_id TO escola_id;
ALTER TABLE pessoas_papeis RENAME COLUMN empresa_id TO escola_id;
ALTER TABLE usuarios       RENAME COLUMN empresa_id TO escola_id;

-- Constraints nomeadas automaticamente pela V1 ainda carregam o nome antigo.
ALTER TABLE pessoas        RENAME CONSTRAINT pessoa_pkey                     TO pessoas_pkey;
ALTER TABLE pessoas_papeis RENAME CONSTRAINT pessoa_papel_pkey               TO pessoas_papeis_pkey;
ALTER TABLE pessoas_papeis RENAME CONSTRAINT pessoa_papel_pessoa_id_papel_key TO pessoas_papeis_pessoa_id_papel_key;
ALTER TABLE pessoas_papeis RENAME CONSTRAINT pessoa_papel_papel_check        TO pessoas_papeis_papel_check;
ALTER TABLE usuarios       RENAME CONSTRAINT usuario_pkey                    TO usuarios_pkey;
ALTER TABLE usuarios       RENAME CONSTRAINT usuario_login_key               TO usuarios_login_key;
ALTER TABLE usuarios       RENAME CONSTRAINT usuario_perfil_check            TO usuarios_perfil_check;


-- ---------------------------------------------------------------------------
-- 4. Reaponta o tenant de EMPRESA para escolas e remove EMPRESA
-- ---------------------------------------------------------------------------
ALTER TABLE pessoas
    DROP CONSTRAINT pessoa_empresa_id_fkey,
    ADD CONSTRAINT pessoas_escola_id_fkey
        FOREIGN KEY (escola_id) REFERENCES escolas (id_escola);

ALTER TABLE pessoas_papeis
    DROP CONSTRAINT pessoa_papel_empresa_id_fkey,
    ADD CONSTRAINT pessoas_papeis_escola_id_fkey
        FOREIGN KEY (escola_id) REFERENCES escolas (id_escola);

ALTER TABLE usuarios
    DROP CONSTRAINT usuario_empresa_id_fkey,
    ADD CONSTRAINT usuarios_escola_id_fkey
        FOREIGN KEY (escola_id) REFERENCES escolas (id_escola);

DROP TABLE empresa;


-- ---------------------------------------------------------------------------
-- 5. TIMESTAMP -> TIMESTAMPTZ
-- ---------------------------------------------------------------------------
-- Os valores existentes foram gravados por CURRENT_TIMESTAMP num container
-- em UTC; a conversao explicita evita depender do fuso da sessao que roda
-- a migration.
ALTER TABLE pessoas        ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';
ALTER TABLE pessoas_papeis ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';
ALTER TABLE usuarios       ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';


-- ---------------------------------------------------------------------------
-- 6. Usuario e papel so podem apontar para pessoa da mesma escola
-- ---------------------------------------------------------------------------
-- A chave estrangeira composta exige que (pessoa_id, escola_id) exista em
-- pessoas com esse mesmo par, o que torna impossivel um usuario da escola A
-- vinculado a uma pessoa da escola B. Substitui a FK simples por pessoa_id.
ALTER TABLE pessoas
    ADD CONSTRAINT pessoas_id_pessoa_escola_id_key UNIQUE (id_pessoa, escola_id);

ALTER TABLE usuarios
    DROP CONSTRAINT usuario_pessoa_id_fkey,
    ADD CONSTRAINT usuarios_pessoa_mesma_escola_fkey
        FOREIGN KEY (pessoa_id, escola_id) REFERENCES pessoas (id_pessoa, escola_id);

ALTER TABLE pessoas_papeis
    DROP CONSTRAINT pessoa_papel_pessoa_id_fkey,
    ADD CONSTRAINT pessoas_papeis_pessoa_mesma_escola_fkey
        FOREIGN KEY (pessoa_id, escola_id) REFERENCES pessoas (id_pessoa, escola_id);
