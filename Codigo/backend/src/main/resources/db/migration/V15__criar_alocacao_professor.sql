-- ---------------------------------------------------------------------------
-- Alocacao de professor: quem leciona cada disciplina de cada turma.
-- ---------------------------------------------------------------------------
-- A FK composta para TURMA_DISCIPLINA garante no banco que a disciplina faz
-- parte da grade da turma; tirar a disciplina da turma (ou apagar a turma)
-- leva a alocacao junto.
--
-- Uma disciplina tem um unico professor por turma. O professor, ao contrario,
-- nao cascateia: alocacao e historico, e exclusao de professor alocado e
-- barrada (ProfessorComHistoricoException) - o caminho e inativar.
CREATE TABLE ALOCACAO_PROFESSOR (
    ID_ALOCACAO     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID       UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    PROFESSOR_ID    UUID NOT NULL REFERENCES PROFESSOR(ID_PROFESSOR),
    TURMA_ID        BIGINT NOT NULL,
    DISCIPLINA_ID   BIGINT NOT NULL,
    CRIADO_EM       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alocacao_turma_disciplina FOREIGN KEY (TURMA_ID, DISCIPLINA_ID)
        REFERENCES turma_disciplina (turma_id, disciplina_id) ON DELETE CASCADE,
    CONSTRAINT uk_alocacao_turma_disciplina UNIQUE (TURMA_ID, DISCIPLINA_ID)
);

CREATE INDEX alocacao_professor_escola_id_idx ON ALOCACAO_PROFESSOR (ESCOLA_ID);
CREATE INDEX alocacao_professor_professor_id_idx ON ALOCACAO_PROFESSOR (PROFESSOR_ID);
