-- ---------------------------------------------------------------------------
-- Tipo da cobranca (IU20-A), para o resumo "Por tipo de cobranca" dos relatorios
-- financeiros (IU19).
-- ---------------------------------------------------------------------------
-- A tela de Nova cobranca ja pedia o tipo, mas so o usava para sugerir a
-- descricao. As cobrancas existentes recebem o tipo pelo inicio da descricao,
-- que e o texto sugerido por essa tela; o que nao casar fica como OUTRO.
ALTER TABLE COBRANCA ADD COLUMN TIPO VARCHAR(12) NOT NULL DEFAULT 'OUTRO';

ALTER TABLE COBRANCA ADD CONSTRAINT ck_cobranca_tipo
    CHECK (TIPO IN ('MENSALIDADE', 'MATRICULA', 'MATERIAL', 'OUTRO'));

UPDATE COBRANCA SET TIPO = CASE
    WHEN DESCRICAO ILIKE 'mensalidade%' THEN 'MENSALIDADE'
    WHEN DESCRICAO ILIKE 'matr_cula%' OR DESCRICAO ILIKE 'taxa de matr_cula%' THEN 'MATRICULA'
    WHEN DESCRICAO ILIKE 'material%' THEN 'MATERIAL'
    ELSE 'OUTRO'
END;
