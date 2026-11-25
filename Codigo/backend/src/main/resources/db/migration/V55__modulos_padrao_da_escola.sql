-- Os modulos da escola passam a restringir o uso (S5-11, RotasPorModulo).
-- Ate aqui eles eram so informativos e a escola nascia sem nenhum; sem esta
-- migration, toda escola cadastrada pela tela perderia financeiro, portais e
-- chamada. Escola sem modulo nenhum recebe todos, como a escola nova passa a
-- nascer; a que o SUPER_ADMIN ja configurou fica como esta.
INSERT INTO ESCOLA_MODULO (ESCOLA_ID, MODULO)
SELECT e.ID_ESCOLA, m.MODULO
FROM ESCOLA e
CROSS JOIN (VALUES ('PORTAL_ALUNO'), ('PORTAL_PROFESSOR'), ('GESTAO_FINANCEIRA'),
                   ('SECRETARIA_VIRTUAL'), ('BIBLIOTECA_DIGITAL'), ('CONTROLE_FREQUENCIA')) AS m (MODULO)
WHERE NOT EXISTS (SELECT 1 FROM ESCOLA_MODULO em WHERE em.ESCOLA_ID = e.ID_ESCOLA);
