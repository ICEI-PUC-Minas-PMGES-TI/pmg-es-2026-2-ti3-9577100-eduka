-- ---------------------------------------------------------------------------
-- Matricula de aluno em turma.
-- ---------------------------------------------------------------------------
-- Um registro por aluno e turma: trancar, cancelar ou concluir muda o STATUS,
-- nao apaga, para a matricula continuar no historico. Reativar reaproveita o
-- mesmo registro; por isso a UNIQUE e por (ALUNO_ID, TURMA_ID).
--
-- So matricula ATIVA ocupa vaga da turma (conferido em MatriculaTurmaService).
--
-- Nada cascateia: aluno e turma com matricula nao somem sem decisao explicita.
CREATE TABLE MATRICULA_TURMA (
    ID_MATRICULA    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID       UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    ALUNO_ID        UUID NOT NULL REFERENCES alunos(id_aluno),
    TURMA_ID        BIGINT NOT NULL REFERENCES turma(id),
    STATUS          VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
    DATA_MATRICULA  DATE NOT NULL DEFAULT CURRENT_DATE,
    OBSERVACAO      VARCHAR(500),
    CRIADO_EM       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM   TIMESTAMPTZ,
    CONSTRAINT ck_matricula_turma_status
        CHECK (STATUS IN ('ATIVA', 'TRANCADA', 'CANCELADA', 'CONCLUIDA')),
    CONSTRAINT uk_matricula_turma_aluno_turma UNIQUE (ALUNO_ID, TURMA_ID)
);

CREATE INDEX matricula_turma_escola_id_idx ON MATRICULA_TURMA (ESCOLA_ID);
CREATE INDEX matricula_turma_turma_id_idx ON MATRICULA_TURMA (TURMA_ID);
