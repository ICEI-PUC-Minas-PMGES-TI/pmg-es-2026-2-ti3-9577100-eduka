-- ---------------------------------------------------------------------------
-- Alunos matriculados, sempre vinculados a uma escola (tenant).
-- ---------------------------------------------------------------------------
-- escola_id NOT NULL + FK: nao existe aluno sem escola, e toda consulta da
-- aplicacao filtra por essa coluna (ver AlunoService / IsolamentoEscola).
-- A matricula e unica por escola, nao globalmente: duas escolas podem usar a
-- mesma numeracao sem colidir.
CREATE TABLE alunos (
    id_aluno              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escola_id             UUID NOT NULL REFERENCES escolas (id_escola),
    nome_aluno            VARCHAR(150) NOT NULL,
    numero_matricula      VARCHAR(50) NOT NULL,
    email_responsavel     VARCHAR(150),
    telefone_responsavel  VARCHAR(20),
    genero_aluno          VARCHAR(30),
    criado_em             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em         TIMESTAMPTZ,
    CONSTRAINT alunos_escola_matricula_key UNIQUE (escola_id, numero_matricula)
);

CREATE INDEX alunos_escola_id_idx ON alunos (escola_id);
