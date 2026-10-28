-- ---------------------------------------------------------------------------
-- Responsaveis (legal/financeiro) dos alunos da escola (tenant).
-- ---------------------------------------------------------------------------
-- Ate aqui o responsavel era so uma PESSOA com papel RESPONSAVEL, criada junto
-- com o aluno (V10). Passa a ter cadastro proprio, como PROFESSOR (V11): dado
-- pessoal (nome, cpf, e-mail, telefone, endereco) continua em PESSOA, cifrado;
-- aqui so o que e do cadastro do responsavel.
--
-- CPF unico por escola nao da para garantir com UNIQUE (coluna cifrada com IV
-- aleatorio): a checagem fica em ResponsavelService.
CREATE TABLE RESPONSAVEL (
    ID_RESPONSAVEL  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID       UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    PESSOA_ID       UUID NOT NULL UNIQUE REFERENCES PESSOA(ID_PESSOA),
    CRIADO_EM       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM   TIMESTAMPTZ
);

CREATE INDEX responsavel_escola_id_idx ON RESPONSAVEL (ESCOLA_ID);

-- Responsaveis ja cadastrados junto com alunos ganham o registro proprio.
INSERT INTO RESPONSAVEL (ESCOLA_ID, PESSOA_ID)
SELECT DISTINCT a.escola_id, ar.PESSOA_ID
  FROM ALUNO_RESPONSAVEL ar
  JOIN alunos a ON a.id_aluno = ar.ALUNO_ID;

-- O vinculo continua pela PESSOA (COBRANCA.RESPONSAVEL_ID tambem aponta para
-- PESSOA), mas so pode ligar ao aluno quem tem cadastro de responsavel.
ALTER TABLE ALUNO_RESPONSAVEL
    ADD CONSTRAINT aluno_responsavel_responsavel_fkey
    FOREIGN KEY (PESSOA_ID) REFERENCES RESPONSAVEL(PESSOA_ID);

-- Responsavel financeiro: quem paga as cobrancas do aluno. No maximo um por
-- aluno; aluno sem nenhum e o que paga as proprias (maior de idade).
ALTER TABLE ALUNO_RESPONSAVEL ADD COLUMN FINANCEIRO BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX aluno_responsavel_financeiro_unico_idx
    ON ALUNO_RESPONSAVEL (ALUNO_ID) WHERE FINANCEIRO;
