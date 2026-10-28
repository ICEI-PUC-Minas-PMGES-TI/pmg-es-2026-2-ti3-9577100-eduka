-- ---------------------------------------------------------------------------
-- Baixa manual de pagamento e controle do lembrete de vencimento.
-- ---------------------------------------------------------------------------
-- PAGA_EM: quando a cobranca foi dada como paga (PATCH /cobrancas/{id}/pagar).
-- Nula enquanto nao paga.
ALTER TABLE COBRANCA ADD COLUMN PAGA_EM TIMESTAMPTZ;

-- LEMBRETE_VENCIMENTO_EM: quando saiu o e-mail de "vencimento proximo" para o
-- vencimento atual. A rotina diaria so avisa quem esta com ela nula, entao
-- reiniciar a aplicacao ou rodar duas vezes no dia nao repete o e-mail. Mudar
-- o vencimento na edicao zera a coluna, para o novo vencimento ser avisado.
ALTER TABLE COBRANCA ADD COLUMN LEMBRETE_VENCIMENTO_EM TIMESTAMPTZ;

-- A rotina procura pendentes ainda nao avisadas com vencimento proximo.
CREATE INDEX cobranca_lembrete_pendente_idx ON COBRANCA (DATA_VENCIMENTO)
    WHERE STATUS = 'PENDENTE' AND LEMBRETE_VENCIMENTO_EM IS NULL;

-- Dar baixa passa a ser operacao auditada.
ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_operacao;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_operacao
    CHECK (OPERACAO IN ('CRIAR', 'EDITAR', 'CANCELAR', 'PAGAR', 'ESTORNAR'));
