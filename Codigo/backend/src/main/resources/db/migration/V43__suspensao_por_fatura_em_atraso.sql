-- Suspensao automatica da escola por fatura da plataforma em atraso (RF24).
--
-- Fatura vencida ha mais de app.assinatura.suspensao.aviso-apos-dias recebe o
-- aviso: AVISO_SUSPENSAO_EM (quando saiu) e SUSPENSAO_PREVISTA_PARA (dia em que
-- a escola sera suspensa se a fatura continuar em aberto). Reativar a escola
-- zera os dois, e o ciclo recomeca.
ALTER TABLE FATURA_PLATAFORMA ADD COLUMN AVISO_SUSPENSAO_EM TIMESTAMPTZ;
ALTER TABLE FATURA_PLATAFORMA ADD COLUMN SUSPENSAO_PREVISTA_PARA DATE;

-- Por que a escola esta suspensa: MANUAL (Super Admin) ou FATURA_EM_ATRASO
-- (rotina). Nulo enquanto ativa. As ja suspensas contam como MANUAL.
ALTER TABLE ESCOLA ADD COLUMN MOTIVO_SUSPENSAO VARCHAR(20);
UPDATE ESCOLA SET MOTIVO_SUSPENSAO = 'MANUAL' WHERE ATIVO = FALSE;
ALTER TABLE ESCOLA ADD CONSTRAINT ck_escola_motivo_suspensao
    CHECK (MOTIVO_SUSPENSAO IN ('MANUAL', 'FATURA_EM_ATRASO'));

-- E-mails ao MASTER: aviso de suspensao e escola suspensa (referencia: fatura).
ALTER TABLE NOTIFICACAO DROP CONSTRAINT ck_notificacao_tipo;
ALTER TABLE NOTIFICACAO ADD CONSTRAINT ck_notificacao_tipo CHECK (TIPO IN ('ACESSO_INICIAL', 'CODIGO_LOGIN',
    'COBRANCA_CRIADA', 'VENCIMENTO_PROXIMO', 'PAGAMENTO_CONFIRMADO', 'AVISO_SUSPENSAO', 'ESCOLA_SUSPENSA'));
