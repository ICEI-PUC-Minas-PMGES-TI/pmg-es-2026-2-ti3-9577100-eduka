-- ---------------------------------------------------------------------------
-- Ordem natural no nome da turma (S5-31): "Turma 2" antes de "Turma 10".
-- ---------------------------------------------------------------------------
-- A lista de turmas e paginada no banco, entao a ordem precisa sair dele: o
-- front so ve uma pagina e nao consegue reordenar entre elas. A collation ICU
-- do portugues com numeros comparados pelo valor (kn) tambem poe acento e
-- maiuscula no lugar certo, como a tela ja ordenava. E deterministica: busca
-- por LIKE e igualdade continuam valendo como antes.
CREATE COLLATION IF NOT EXISTS nome_natural (provider = icu, locale = 'pt-BR-u-kn-true');

ALTER TABLE turma ALTER COLUMN nome TYPE VARCHAR(100) COLLATE nome_natural;
