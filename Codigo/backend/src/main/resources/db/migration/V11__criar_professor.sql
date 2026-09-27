-- ---------------------------------------------------------------------------
-- Professores da escola (tenant), no mesmo schema unico das demais tabelas.
-- ---------------------------------------------------------------------------
-- Dado pessoal (nome, cpf, e-mail, telefone) fica em PESSOA, cifrado - aqui
-- so o que e do contexto academico. O login do professor, quando existir, e o
-- USUARIO com o mesmo PESSOA_ID: nao ha coluna USUARIO_ID aqui.
--
-- CPF unico por escola nao da para garantir com UNIQUE: a coluna e cifrada
-- com IV aleatorio, entao o mesmo CPF gera bytes diferentes. A checagem fica
-- na aplicacao (ProfessorService).
CREATE TABLE PROFESSOR (
    ID_PROFESSOR    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID       UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    PESSOA_ID       UUID NOT NULL UNIQUE REFERENCES PESSOA(ID_PESSOA),
    FORMACAO        VARCHAR(150),
    DATA_ADMISSAO   DATE,
    ATIVO           BOOLEAN NOT NULL DEFAULT TRUE,
    CRIADO_EM       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM   TIMESTAMPTZ
);

CREATE INDEX professor_escola_id_idx ON PROFESSOR (ESCOLA_ID);
