-- ---------------------------------------------------------------------------
-- Inativar e reativar aluno (PATCH /alunos/{id}/inativar e /ativar).
-- ---------------------------------------------------------------------------
-- Aluno que saiu da escola mas ainda nao deve ser anonimizado (ex.: tem
-- cobranca em aberto ou pode voltar): o registro e o dado pessoal ficam, o
-- login fica bloqueado e ele nao pode ser matriculado ate ser reativado.
-- Todo aluno ja cadastrado comeca ativo.
ALTER TABLE alunos ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE;
