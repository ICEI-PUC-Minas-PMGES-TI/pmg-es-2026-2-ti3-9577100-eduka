-- ---------------------------------------------------------------------------
-- Aulas dadas pelo professor e a frequencia (chamada) dos alunos em cada uma.
-- ---------------------------------------------------------------------------
-- A aula e de uma disciplina numa turma. Como em AVALIACAO (V19), a FK composta
-- para TURMA_DISCIPLINA garante que a disciplina faz parte da grade da turma, e
-- quem gerencia e o professor alocado hoje no par turma/disciplina (conferido
-- em AulaService, via ALOCACAO_PROFESSOR); PROFESSOR_ID guarda quem registrou.
-- A aula nao aponta para a linha de ALOCACAO_PROFESSOR de proposito: desalocar
-- apaga aquela linha (V15), e o historico de aulas e faltas deve sobreviver a
-- troca de professor.
--
-- Nada cascateia: aula com chamada e historico do aluno, e tirar da grade uma
-- disciplina que ja tem aula e barrado pela FK.
CREATE TABLE AULA (
    ID_AULA         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID       UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    TURMA_ID        BIGINT NOT NULL,
    DISCIPLINA_ID   BIGINT NOT NULL,
    PROFESSOR_ID    UUID NOT NULL REFERENCES PROFESSOR(ID_PROFESSOR),
    DATA_AULA       DATE NOT NULL,
    CONTEUDO        VARCHAR(2000) NOT NULL,
    CRIADO_EM       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM   TIMESTAMPTZ,
    CONSTRAINT fk_aula_turma_disciplina FOREIGN KEY (TURMA_ID, DISCIPLINA_ID)
        REFERENCES turma_disciplina (turma_id, disciplina_id),
    CONSTRAINT ck_aula_conteudo CHECK (LENGTH(TRIM(CONTEUDO)) > 0)
);

CREATE INDEX aula_escola_id_idx ON AULA (ESCOLA_ID);
-- Listagem do professor (mais recentes primeiro) e soma de faltas por turma e disciplina.
CREATE INDEX aula_turma_disciplina_data_idx ON AULA (TURMA_ID, DISCIPLINA_ID, DATA_AULA);

-- Uma linha por aluno em cada aula: corrigir a chamada atualiza o mesmo registro.
-- PRESENTE = false e a falta.
CREATE TABLE FREQUENCIA (
    ID_FREQUENCIA   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID       UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    AULA_ID         UUID NOT NULL REFERENCES AULA(ID_AULA),
    ALUNO_ID        UUID NOT NULL REFERENCES alunos(id_aluno),
    PRESENTE        BOOLEAN NOT NULL,
    CRIADO_EM       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM   TIMESTAMPTZ,
    CONSTRAINT uk_frequencia_aula_aluno UNIQUE (AULA_ID, ALUNO_ID)
);

CREATE INDEX frequencia_escola_id_idx ON FREQUENCIA (ESCOLA_ID);
-- Frequencia de um aluno (boletim): com AULA_ID e PRESENTE no indice, o join com AULA
-- e a soma de faltas nao precisam ler a tabela.
CREATE INDEX frequencia_aluno_aula_idx ON FREQUENCIA (ALUNO_ID, AULA_ID, PRESENTE);
