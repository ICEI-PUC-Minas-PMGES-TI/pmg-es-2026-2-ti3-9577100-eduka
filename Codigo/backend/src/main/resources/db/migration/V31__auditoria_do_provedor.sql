-- ---------------------------------------------------------------------------
-- Auditoria do que vem do provedor de pagamento (RF16.1).
-- ---------------------------------------------------------------------------
-- O pagamento confirmado pelo Asaas chega pelo webhook, sem usuario: a linha
-- do log passa a ter ORIGEM. USUARIO e o que ja existia (alguem logado fez a
-- operacao); PROVEDOR e o aviso do Asaas, sem autor. So a origem PROVEDOR
-- pode ficar sem USUARIO_ID, entao operacao de usuario continua exigindo autor.
ALTER TABLE LOG_AUDITORIA ADD COLUMN ORIGEM VARCHAR(10) NOT NULL DEFAULT 'USUARIO';
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_origem CHECK (ORIGEM IN ('USUARIO', 'PROVEDOR'));

ALTER TABLE LOG_AUDITORIA ALTER COLUMN USUARIO_ID DROP NOT NULL;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_autor
    CHECK ((ORIGEM = 'USUARIO') = (USUARIO_ID IS NOT NULL));

-- Pagamento confirmado pelo provedor (a baixa manual continua sendo PAGAR).
ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_operacao;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_operacao
    CHECK (OPERACAO IN ('CRIAR', 'EDITAR', 'CANCELAR', 'PAGAR', 'CONFIRMAR_PAGAMENTO', 'ESTORNAR'));
