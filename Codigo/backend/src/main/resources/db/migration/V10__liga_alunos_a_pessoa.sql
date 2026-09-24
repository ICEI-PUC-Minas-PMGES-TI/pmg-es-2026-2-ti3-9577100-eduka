-- alunos (V5) duplicava nome/email/telefone como colunas proprias, todas em
-- texto puro. Passa a referenciar PESSOA (cifrada) para o dado do proprio
-- aluno; responsavel(is) viram PESSOA(s) ligada(s) via ALUNO_RESPONSAVEL, ja
-- que o responsavel legal do menor tambem e titular de dado pessoal (LGPD).
ALTER TABLE alunos ADD COLUMN pessoa_id UUID REFERENCES PESSOA(ID_PESSOA);
-- Sem dado real (so mock local): nao ha linha existente para migrar.
ALTER TABLE alunos ALTER COLUMN pessoa_id SET NOT NULL;

ALTER TABLE alunos DROP COLUMN nome_aluno;
ALTER TABLE alunos DROP COLUMN email_responsavel;
ALTER TABLE alunos DROP COLUMN telefone_responsavel;

CREATE TABLE ALUNO_RESPONSAVEL (
    ALUNO_ID    UUID NOT NULL REFERENCES alunos(id_aluno),
    PESSOA_ID   UUID NOT NULL REFERENCES PESSOA(ID_PESSOA),
    PARENTESCO  VARCHAR(30),
    PRIMARY KEY (ALUNO_ID, PESSOA_ID)
);
