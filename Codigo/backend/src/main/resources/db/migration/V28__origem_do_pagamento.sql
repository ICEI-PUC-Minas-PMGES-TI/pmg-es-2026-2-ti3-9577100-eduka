-- ---------------------------------------------------------------------------
-- Origem do pagamento da cobranca.
-- ---------------------------------------------------------------------------
-- ONLINE: confirmado pelo provedor (webhook ou reconciliacao). MANUAL fica
-- reservado para a baixa manual com comprovante, que ainda vai relaxar as
-- colunas que hoje so fazem sentido para o provedor (ID_TRANSACAO_EXTERNA).
ALTER TABLE PAGAMENTO_COBRANCA ADD COLUMN ORIGEM VARCHAR(6) NOT NULL DEFAULT 'ONLINE'
    CHECK (ORIGEM IN ('ONLINE', 'MANUAL'));
