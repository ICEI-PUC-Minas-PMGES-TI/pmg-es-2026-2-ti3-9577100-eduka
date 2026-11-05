-- ---------------------------------------------------------------------------
-- Pedido de correcao de dados do aluno (IU15): o aluno so consulta os
-- proprios dados e pede a secretaria que corrija. Uma solicitacao aberta por
-- aluno de cada vez (indice parcial); a secretaria marca como atendida.
-- ---------------------------------------------------------------------------
CREATE TABLE SOLICITACAO_CORRECAO (
    ID_SOLICITACAO_CORRECAO  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID                UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    ALUNO_ID                 UUID NOT NULL REFERENCES alunos(id_aluno),
    MENSAGEM                 VARCHAR(500) NOT NULL,
    STATUS                   VARCHAR(10) NOT NULL DEFAULT 'ABERTA'
        CHECK (STATUS IN ('ABERTA', 'ATENDIDA')),
    CRIADO_EM                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATENDIDA_EM              TIMESTAMPTZ,
    CHECK ((STATUS = 'ATENDIDA') = (ATENDIDA_EM IS NOT NULL))
);

CREATE UNIQUE INDEX solicitacao_correcao_aberta_uk ON SOLICITACAO_CORRECAO (ALUNO_ID) WHERE STATUS = 'ABERTA';
CREATE INDEX solicitacao_correcao_escola_status_idx ON SOLICITACAO_CORRECAO (ESCOLA_ID, STATUS);
