-- ---------------------------------------------------------------------------
-- Encerrar periodo e abrir matriculas (IU09).
-- ENCERRADO_EM: a secretaria encerrou o periodo em andamento antes do fim;
-- encerrado, ele nao recebe matricula nova.
-- MATRICULAS_ABERTAS: periodo planejado (ainda nao comecou) so recebe
-- matricula depois de Abrir matriculas. Os periodos que ja existiam ficam
-- abertos, como funcionavam; o cadastro novo de periodo futuro comeca fechado.
-- ---------------------------------------------------------------------------
ALTER TABLE periodo_letivo ADD COLUMN matriculas_abertas BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE periodo_letivo ADD COLUMN encerrado_em DATE;
