-- Tela de edicao de escola (IU03): endereco quebrado em campos, vigencia da
-- assinatura, limite de usuarios e modulos habilitados.

-- O texto livre que ja existia em ENDERECO fica no logradouro; os demais
-- campos do endereco nascem vazios e sao completados pela tela de edicao.
ALTER TABLE ESCOLA RENAME COLUMN ENDERECO TO LOGRADOURO;

ALTER TABLE ESCOLA
    ADD COLUMN NUMERO          VARCHAR(10),
    ADD COLUMN BAIRRO          VARCHAR(100),
    ADD COLUMN CIDADE          VARCHAR(100),
    ADD COLUMN ESTADO          VARCHAR(2),
    ADD COLUMN CEP             VARCHAR(8),
    ADD COLUMN INICIO_VIGENCIA DATE,
    ADD COLUMN FIM_VIGENCIA    DATE,
    ADD COLUMN LIMITE_USUARIOS INTEGER CHECK (LIMITE_USUARIOS > 0),
    ADD CONSTRAINT ESCOLA_VIGENCIA_CHECK
        CHECK (INICIO_VIGENCIA IS NULL OR FIM_VIGENCIA IS NULL OR FIM_VIGENCIA >= INICIO_VIGENCIA);

-- Os nomes precisam bater com o enum ModuloEscola.
CREATE TABLE ESCOLA_MODULO (
    ESCOLA_ID  UUID NOT NULL REFERENCES ESCOLA (ID_ESCOLA) ON DELETE CASCADE,
    MODULO     TEXT NOT NULL CHECK (MODULO IN
                  ('PORTAL_ALUNO', 'PORTAL_PROFESSOR', 'GESTAO_FINANCEIRA',
                   'SECRETARIA_VIRTUAL', 'BIBLIOTECA_DIGITAL', 'CONTROLE_FREQUENCIA')),
    PRIMARY KEY (ESCOLA_ID, MODULO)
);
