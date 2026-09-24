-- PESSOA (V1) guardava BYTEA mas nunca cifrava de verdade (mock.sql gravava
-- com convert_to, texto puro). Sem dado real de producao neste projeto (so
-- mock local) - descarta sem migracao de dados.
TRUNCATE USUARIO, PESSOA_PAPEL, PESSOA CASCADE;
ALTER TABLE USUARIO DROP CONSTRAINT usuario_pessoa_id_fkey;
DROP TABLE PESSOA_PAPEL;
DROP TABLE PESSOA;

-- Esvazia alunos tambem: V10 vai exigir pessoa_id NOT NULL, e uma linha
-- pre-existente (ex.: cadastro de teste num volume local ja em uso) faria
-- esse ALTER falhar. Sem dado real (so mock local, que nao popula alunos).
TRUNCATE alunos;
