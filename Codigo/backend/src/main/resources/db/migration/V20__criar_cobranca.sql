-- ---------------------------------------------------------------------------
-- Cobranca lancada para um aluno (mensalidade, material, taxa...).
-- ---------------------------------------------------------------------------
-- A cobranca e sempre de um aluno. MATRICULA_ID liga a cobranca a turma que a
-- originou e RESPONSAVEL_ID diz quem paga; os dois sao opcionais, porque ha
-- cobranca avulsa e aluno maior de idade sem responsavel. Que a matricula e o
-- responsavel sao daquele aluno e conferido em CobrancaService.
--
-- Cancelar muda o STATUS, nao apaga: a cobranca continua no historico
-- financeiro do aluno. "Em atraso" nao e gravado: e a cobranca PENDENTE com
-- DATA_VENCIMENTO no passado, calculado na leitura.
--
-- Nada cascateia: aluno, matricula e responsavel com cobranca nao somem sem
-- decisao explicita.
CREATE TABLE COBRANCA (
    ID_COBRANCA      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID        UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    ALUNO_ID         UUID NOT NULL REFERENCES alunos(id_aluno),
    MATRICULA_ID     UUID REFERENCES MATRICULA_TURMA(ID_MATRICULA),
    RESPONSAVEL_ID   UUID REFERENCES PESSOA(ID_PESSOA),
    DESCRICAO        VARCHAR(255) NOT NULL,
    VALOR            NUMERIC(10, 2) NOT NULL,
    DATA_VENCIMENTO  DATE NOT NULL,
    STATUS           VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    CANCELADA_EM     TIMESTAMPTZ,
    CRIADO_EM        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM    TIMESTAMPTZ,
    CONSTRAINT ck_cobranca_status CHECK (STATUS IN ('PENDENTE', 'PAGA', 'CANCELADA')),
    CONSTRAINT ck_cobranca_valor CHECK (VALOR > 0)
);

CREATE INDEX cobranca_escola_id_idx ON COBRANCA (ESCOLA_ID);
CREATE INDEX cobranca_aluno_id_idx ON COBRANCA (ALUNO_ID);
CREATE INDEX cobranca_vencimento_idx ON COBRANCA (ESCOLA_ID, DATA_VENCIMENTO);
