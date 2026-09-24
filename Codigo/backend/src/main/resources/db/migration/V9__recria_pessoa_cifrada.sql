-- Toda coluna sigilosa fica em BYTEA, cifrada pela aplicacao (AES-256-GCM
-- via PessoaCryptoConverter) antes de chegar aqui - o Postgres nunca ve
-- texto puro nem a chave. ESCOLA_ID fica nullable: SUPER_ADMIN tem PESSOA
-- propria sem escola, igual USUARIO (V3).
CREATE TABLE PESSOA (
    ID_PESSOA         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID         UUID REFERENCES ESCOLA(ID_ESCOLA),
    NOME              BYTEA NOT NULL,
    CPF               BYTEA,
    DATA_NASCIMENTO   BYTEA,
    EMAIL             BYTEA,
    TELEFONE          BYTEA,
    LOGRADOURO        BYTEA,
    NUMERO            BYTEA,
    BAIRRO            BYTEA,
    CIDADE            BYTEA,
    ESTADO            BYTEA,
    CEP               BYTEA,
    CHAVE_VERSAO      SMALLINT NOT NULL DEFAULT 1,
    ATIVO             BOOLEAN NOT NULL DEFAULT TRUE,
    CRIADO_EM         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Toda listagem por pessoa (ex.: alunos da escola) filtra por aqui.
CREATE INDEX pessoa_escola_id_idx ON PESSOA (ESCOLA_ID);

CREATE TABLE PESSOA_PAPEL (
    ID_PESSOA_PAPEL   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    PESSOA_ID         UUID NOT NULL REFERENCES PESSOA(ID_PESSOA),
    PAPEL             TEXT NOT NULL CHECK (PAPEL IN
                         ('ALUNO', 'RESPONSAVEL', 'PROFESSOR', 'FUNCIONARIO', 'USUARIO')),
    CRIADO_EM         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (PESSOA_ID, PAPEL)
);

ALTER TABLE USUARIO
    ADD CONSTRAINT usuario_pessoa_id_fkey FOREIGN KEY (PESSOA_ID) REFERENCES PESSOA(ID_PESSOA);
