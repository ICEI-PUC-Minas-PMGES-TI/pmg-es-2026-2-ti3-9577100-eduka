-- ---------------------------------------------------------------------------
-- Auditoria do que vem do Asaas e da ativacao da conta da escola (RF22.2).
-- ---------------------------------------------------------------------------
-- Entidades novas no log:
--   CONTA_RECEBIMENTO: a conta filha da escola (CONTA_RECEBIMENTO_ESCOLA).
--   FATURA_PLATAFORMA: a fatura que a escola paga ao Eduka (FATURA_PLATAFORMA).
--
-- Operacoes novas:
--   ATIVAR: o MASTER pediu a criacao da conta filha (origem USUARIO).
--   ALTERAR_SITUACAO: o Asaas mudou a situacao da conta (origem PROVEDOR).
--   CONTESTAR: o pagador abriu uma contestacao no Asaas (origem PROVEDOR).
-- O estorno feito no Asaas usa ESTORNAR, que ja existia; a remocao da cobranca
-- no Asaas usa CANCELAR com origem PROVEDOR.
ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_operacao;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_operacao
    CHECK (OPERACAO IN ('CRIAR', 'EDITAR', 'CANCELAR', 'PAGAR', 'CONFIRMAR_PAGAMENTO', 'PAGO_APOS_CANCELAR',
                        'ESTORNAR', 'CONTESTAR', 'ATIVAR', 'ALTERAR_SITUACAO'));

ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_entidade;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_entidade
    CHECK (ENTIDADE IN ('COBRANCA', 'PAGAMENTO', 'DESPESA', 'CONTA_RECEBIMENTO', 'FATURA_PLATAFORMA'));
