-- Migration V14: Tabelas da estrutura acadêmica da Secretaria

CREATE TABLE periodo_letivo (
    id BIGSERIAL PRIMARY KEY,
    escola_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_periodo_letivo_escola FOREIGN KEY (escola_id) REFERENCES escola(id_escola) ON DELETE CASCADE
);

CREATE TABLE curso (
    id BIGSERIAL PRIMARY KEY,
    escola_id UUID NOT NULL,
    nome VARCHAR(150) NOT NULL,
    codigo VARCHAR(50),
    descricao VARCHAR(500),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_curso_escola FOREIGN KEY (escola_id) REFERENCES escola(id_escola) ON DELETE CASCADE
);

CREATE TABLE disciplina (
    id BIGSERIAL PRIMARY KEY,
    escola_id UUID NOT NULL,
    curso_id BIGINT,
    nome VARCHAR(150) NOT NULL,
    codigo VARCHAR(50),
    carga_horaria INT NOT NULL,
    descricao VARCHAR(500),
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_disciplina_escola FOREIGN KEY (escola_id) REFERENCES escola(id_escola) ON DELETE CASCADE,
    CONSTRAINT fk_disciplina_curso FOREIGN KEY (curso_id) REFERENCES curso(id) ON DELETE SET NULL
);

CREATE TABLE turma (
    id BIGSERIAL PRIMARY KEY,
    escola_id UUID NOT NULL,
    periodo_letivo_id BIGINT NOT NULL,
    curso_id BIGINT NOT NULL,
    nome VARCHAR(100) NOT NULL,
    codigo VARCHAR(50),
    vagas INT NOT NULL DEFAULT 0,
    turno VARCHAR(30) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_turma_escola FOREIGN KEY (escola_id) REFERENCES escola(id_escola) ON DELETE CASCADE,
    CONSTRAINT fk_turma_periodo FOREIGN KEY (periodo_letivo_id) REFERENCES periodo_letivo(id),
    CONSTRAINT fk_turma_curso FOREIGN KEY (curso_id) REFERENCES curso(id)
);

CREATE TABLE turma_disciplina (
    turma_id BIGINT NOT NULL,
    disciplina_id BIGINT NOT NULL,
    PRIMARY KEY (turma_id, disciplina_id),
    CONSTRAINT fk_td_turma FOREIGN KEY (turma_id) REFERENCES turma(id) ON DELETE CASCADE,
    CONSTRAINT fk_td_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id) ON DELETE CASCADE
);

-- Índices otimizados para busca por Tenant (Escola)
CREATE INDEX idx_periodo_letivo_escola ON periodo_letivo(escola_id);
CREATE INDEX idx_curso_escola ON curso(escola_id);
CREATE INDEX idx_disciplina_escola ON disciplina(escola_id);
CREATE INDEX idx_turma_escola ON turma(escola_id);
