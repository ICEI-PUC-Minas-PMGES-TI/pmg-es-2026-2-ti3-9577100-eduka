-- ---------------------------------------------------------------------------
-- Pagamento depois do cancelamento passa a ficar sinalizado na auditoria (RF16.1).
-- ---------------------------------------------------------------------------
-- A cobranca cancelada no Eduka que o provedor recebeu mesmo assim vira PAGA
-- (o dinheiro prevalece, R23) e entra no log como PAGO_APOS_CANCELAR, com
-- ORIGEM = PROVEDOR (V31), para a escola achar e resolver com o pagador.
ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_operacao;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_operacao
    CHECK (OPERACAO IN ('CRIAR', 'EDITAR', 'CANCELAR', 'PAGAR', 'CONFIRMAR_PAGAMENTO', 'PAGO_APOS_CANCELAR',
                        'ESTORNAR'));
