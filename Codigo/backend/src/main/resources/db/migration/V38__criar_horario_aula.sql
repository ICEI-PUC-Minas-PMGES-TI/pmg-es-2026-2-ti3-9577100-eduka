-- ---------------------------------------------------------------------------
-- Horario das aulas (IU13 e IU10): em que dia e a que horas cada disciplina
-- da turma tem aula. Fica na turma e na disciplina, nao na alocacao: trocar o
-- professor (a alocacao e apagada e recriada) nao perde o horario.
-- DIA_SEMANA segue a ISO-8601: 1 = segunda ... 6 = sabado.
-- A turma nao tem duas aulas no mesmo dia e hora.
-- Horario e configuracao: some junto com a turma ou a disciplina.
-- ---------------------------------------------------------------------------
CREATE TABLE HORARIO_AULA (
    ID_HORARIO_AULA  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID        UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    TURMA_ID         BIGINT NOT NULL REFERENCES turma(id) ON DELETE CASCADE,
    DISCIPLINA_ID    BIGINT NOT NULL REFERENCES disciplina(id) ON DELETE CASCADE,
    DIA_SEMANA       SMALLINT NOT NULL CHECK (DIA_SEMANA BETWEEN 1 AND 6),
    HORA_INICIO      TIME NOT NULL
);

CREATE UNIQUE INDEX horario_aula_turma_dia_hora_uk ON HORARIO_AULA (TURMA_ID, DIA_SEMANA, HORA_INICIO);
CREATE INDEX horario_aula_escola_turma_idx ON HORARIO_AULA (ESCOLA_ID, TURMA_ID);
