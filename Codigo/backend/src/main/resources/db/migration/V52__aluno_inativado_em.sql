-- ---------------------------------------------------------------------------
-- Politica de retencao (LGPD): quando o aluno foi inativado.
-- ---------------------------------------------------------------------------
-- A rotina de retencao anonimiza o aluno que esta inativo ha mais do que o
-- prazo da politica (app.lgpd.retencao.anos, 5 anos por padrao), contado a
-- partir daqui. Nulo para o aluno ativo; reativar apaga a data.
ALTER TABLE alunos ADD COLUMN IF NOT EXISTS inativado_em TIMESTAMPTZ;

-- Quem ja estava inativo comeca a contar o prazo agora: nenhum aluno e
-- anonimizado sem a escola ter tido o prazo inteiro para reativar.
UPDATE alunos SET inativado_em = NOW() WHERE ativo = FALSE AND inativado_em IS NULL;
