-- ---------------------------------------------------------------------------
-- Rascunho de notas (IU14): o professor guarda as notas sem publicar. Com
-- NOTAS_PUBLICADAS falso, o boletim e a agenda do aluno nao mostram as notas
-- da avaliacao; Lancar notas publica. As avaliacoes que ja existiam ficam
-- publicadas, como estavam.
-- ---------------------------------------------------------------------------
ALTER TABLE AVALIACAO ADD COLUMN NOTAS_PUBLICADAS BOOLEAN NOT NULL DEFAULT TRUE;
