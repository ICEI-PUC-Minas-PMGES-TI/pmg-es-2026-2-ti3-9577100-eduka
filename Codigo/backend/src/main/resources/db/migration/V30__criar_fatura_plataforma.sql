-- ---------------------------------------------------------------------------
-- Fatura da plataforma (Fluxo A): o que a escola paga ao Eduka.
-- ---------------------------------------------------------------------------
-- Lancada pelo Super Admin e paga pelo MASTER da escola. O dinheiro vai para a
-- conta-pai do Eduka, nao para a conta filha da escola. Nunca entra em painel
-- ou indicador da escola (R27). Uma fatura por escola e competencia, fora as
-- canceladas (R26). O pagamento fica na propria fatura: nao ha
-- PAGAMENTO_COBRANCA no Fluxo A.
CREATE TABLE FATURA_PLATAFORMA (
    ID_FATURA             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID             UUID NOT NULL REFERENCES ESCOLA (ID_ESCOLA),
    COMPETENCIA           DATE NOT NULL CHECK (EXTRACT(DAY FROM COMPETENCIA) = 1),
    DESCRICAO             VARCHAR(255) NOT NULL,
    VALOR                 NUMERIC(10,2) NOT NULL CHECK (VALOR > 0),
    DATA_VENCIMENTO       DATE NOT NULL,
    STATUS                VARCHAR(10) NOT NULL DEFAULT 'PENDENTE'
                          CHECK (STATUS IN ('PENDENTE', 'EM_ATRASO', 'PAGA', 'CANCELADA')),
    SITUACAO_PROVEDOR     VARCHAR(15) NOT NULL DEFAULT 'NAO_EMITIDA'
                          CHECK (SITUACAO_PROVEDOR IN ('NAO_EMITIDA', 'EMITINDO', 'EMITIDA', 'FALHA_EMISSAO')),
    FORMA_PAGAMENTO       VARCHAR(6) CHECK (FORMA_PAGAMENTO IN ('PIX', 'BOLETO')),
    ID_COBRANCA_EXTERNA   VARCHAR(64) UNIQUE,
    EMISSAO_INICIADA_EM   TIMESTAMPTZ,
    EMITIDA_EM            TIMESTAMPTZ,
    ULTIMO_ERRO_PROVEDOR  VARCHAR(500),
    VALOR_PAGO            NUMERIC(10,2),
    DATA_PAGAMENTO        DATE,
    ID_TRANSACAO_EXTERNA  VARCHAR(100),
    URL_RECIBO_PROVEDOR   VARCHAR(500),
    CRIADA_POR            UUID NOT NULL REFERENCES USUARIO (ID_USUARIO),
    CANCELADA_EM          TIMESTAMPTZ,
    PAGA_EM               TIMESTAMPTZ,
    CRIADO_EM             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM         TIMESTAMPTZ,
    VERSAO                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fatura_paga CHECK (STATUS <> 'PAGA' OR (VALOR_PAGO IS NOT NULL AND DATA_PAGAMENTO IS NOT NULL)),
    CONSTRAINT ck_fatura_emitida
        CHECK (SITUACAO_PROVEDOR <> 'EMITIDA' OR (ID_COBRANCA_EXTERNA IS NOT NULL AND FORMA_PAGAMENTO IS NOT NULL))
);
CREATE UNIQUE INDEX fatura_competencia_unica_idx ON FATURA_PLATAFORMA (ESCOLA_ID, COMPETENCIA)
    WHERE STATUS <> 'CANCELADA';
CREATE INDEX fatura_emitindo_idx ON FATURA_PLATAFORMA (EMISSAO_INICIADA_EM) WHERE SITUACAO_PROVEDOR = 'EMITINDO';
