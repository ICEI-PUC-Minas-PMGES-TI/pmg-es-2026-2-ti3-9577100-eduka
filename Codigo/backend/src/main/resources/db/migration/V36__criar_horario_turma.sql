-- ---------------------------------------------------------------------------
-- Horario das turmas (IU26): a grade semanal montada pela secretaria.
-- ---------------------------------------------------------------------------
-- Cada linha e uma aula da grade: uma alocacao (que ja diz turma, disciplina e
-- professor), o dia da semana (segunda a sabado) e o intervalo de horas. A
-- mesma alocacao pode aparecer varias vezes na semana.
--
-- Que a alocacao e da turma informada e que nao ha sobreposicao (na turma ou
-- para o professor, no mesmo dia) e conferido em HorarioTurmaService. Aulas
-- que so se encostam (fim = inicio da outra) nao conflitam.
--
-- Desalocar o professor (ou tirar a disciplina da turma, ou apagar a turma)
-- leva a alocacao e, com ela, as aulas da grade.
CREATE TABLE HORARIO_TURMA (
    ID_HORARIO_TURMA  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID         UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    ALOCACAO_ID       UUID NOT NULL REFERENCES ALOCACAO_PROFESSOR(ID_ALOCACAO) ON DELETE CASCADE,
    DIA_SEMANA        VARCHAR(10) NOT NULL,
    HORA_INICIO       TIME NOT NULL,
    HORA_FIM          TIME NOT NULL,
    CRIADO_EM         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM     TIMESTAMPTZ,
    CONSTRAINT ck_horario_turma_dia
        CHECK (DIA_SEMANA IN ('SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO')),
    CONSTRAINT ck_horario_turma_horas CHECK (HORA_INICIO < HORA_FIM)
);

CREATE INDEX horario_turma_escola_id_idx ON HORARIO_TURMA (ESCOLA_ID);
CREATE INDEX horario_turma_alocacao_id_idx ON HORARIO_TURMA (ALOCACAO_ID);
