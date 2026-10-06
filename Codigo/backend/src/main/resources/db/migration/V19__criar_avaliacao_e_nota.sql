-- ---------------------------------------------------------------------------
-- Avaliacoes (provas, trabalhos, tarefas) e notas dos alunos.
-- ---------------------------------------------------------------------------
-- A avaliacao e de uma disciplina numa turma. A FK composta para
-- TURMA_DISCIPLINA garante que a disciplina faz parte da grade da turma. Quem
-- gerencia e o professor alocado no par turma/disciplina (conferido em
-- AvaliacaoService); PROFESSOR_ID guarda quem criou.
--
-- VALOR e a pontuacao maxima da avaliacao; a nota de cada aluno vai de 0 ate
-- ele (conferido no servico, porque depende da avaliacao).
--
-- Nada cascateia: avaliacao com nota e historico do aluno. Excluir avaliacao
-- com nota lancada e barrado (AvaliacaoComNotasException), e tirar da grade
-- uma disciplina que ja tem avaliacao e barrado pela FK.
CREATE TABLE AVALIACAO (
    ID_AVALIACAO    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID       UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    TURMA_ID        BIGINT NOT NULL,
    DISCIPLINA_ID   BIGINT NOT NULL,
    PROFESSOR_ID    UUID NOT NULL REFERENCES PROFESSOR(ID_PROFESSOR),
    TIPO            VARCHAR(20) NOT NULL,
    TITULO          VARCHAR(150) NOT NULL,
    DESCRICAO       VARCHAR(500),
    DATA_AVALIACAO  DATE NOT NULL,
    VALOR           NUMERIC(5, 2) NOT NULL,
    CRIADO_EM       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM   TIMESTAMPTZ,
    CONSTRAINT fk_avaliacao_turma_disciplina FOREIGN KEY (TURMA_ID, DISCIPLINA_ID)
        REFERENCES turma_disciplina (turma_id, disciplina_id),
    CONSTRAINT ck_avaliacao_tipo CHECK (TIPO IN ('PROVA', 'TRABALHO', 'TAREFA')),
    CONSTRAINT ck_avaliacao_valor CHECK (VALOR > 0)
);

CREATE INDEX avaliacao_escola_id_idx ON AVALIACAO (ESCOLA_ID);
CREATE INDEX avaliacao_turma_disciplina_idx ON AVALIACAO (TURMA_ID, DISCIPLINA_ID);

-- Uma nota por aluno em cada avaliacao: relancar atualiza o mesmo registro.
CREATE TABLE NOTA (
    ID_NOTA         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID       UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    AVALIACAO_ID    UUID NOT NULL REFERENCES AVALIACAO(ID_AVALIACAO),
    ALUNO_ID        UUID NOT NULL REFERENCES alunos(id_aluno),
    VALOR           NUMERIC(5, 2) NOT NULL,
    OBSERVACAO      VARCHAR(500),
    CRIADO_EM       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM   TIMESTAMPTZ,
    CONSTRAINT ck_nota_valor CHECK (VALOR >= 0),
    CONSTRAINT uk_nota_avaliacao_aluno UNIQUE (AVALIACAO_ID, ALUNO_ID)
);

CREATE INDEX nota_escola_id_idx ON NOTA (ESCOLA_ID);
CREATE INDEX nota_aluno_id_idx ON NOTA (ALUNO_ID);
