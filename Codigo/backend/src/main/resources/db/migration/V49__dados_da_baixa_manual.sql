-- ---------------------------------------------------------------------------
-- Baixa manual com valor, data e forma (UC12, RF16).
-- ---------------------------------------------------------------------------
-- O pagamento feito fora do Eduka fica na propria cobranca, e nao em
-- PAGAMENTO_COBRANCA: la o webhook e a reconciliacao tratam a linha como prova
-- de que o provedor recebeu.
--
-- VALOR_PAGO_MANUAL: o que a escola recebeu, que pode ser diferente do valor
-- da cobranca (desconto, juros). FORMA_PAGAMENTO_MANUAL: como recebeu. A data
-- do pagamento e o PAGA_EM: com data de hoje, o momento da baixa; com data
-- passada, a meia-noite daquele dia no fuso da escola (a hora nao e conhecida).
-- Os dois sao nulos enquanto a cobranca nao tem baixa manual.
ALTER TABLE COBRANCA ADD COLUMN VALOR_PAGO_MANUAL NUMERIC(10,2)
    CHECK (VALOR_PAGO_MANUAL > 0);
ALTER TABLE COBRANCA ADD COLUMN FORMA_PAGAMENTO_MANUAL VARCHAR(13)
    CHECK (FORMA_PAGAMENTO_MANUAL IN ('DINHEIRO', 'PIX', 'TRANSFERENCIA', 'CARTAO', 'BOLETO'));

-- Baixas feitas antes desta versao: o valor recebido era o da cobranca. A
-- forma nao foi informada e fica nula.
UPDATE COBRANCA c SET VALOR_PAGO_MANUAL = c.VALOR
 WHERE c.STATUS = 'PAGA' AND c.PAGA_EM IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM PAGAMENTO_COBRANCA p WHERE p.COBRANCA_ID = c.ID_COBRANCA);
