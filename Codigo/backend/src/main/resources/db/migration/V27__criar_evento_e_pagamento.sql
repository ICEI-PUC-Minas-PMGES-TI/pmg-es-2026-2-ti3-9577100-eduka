-- ---------------------------------------------------------------------------
-- Eventos do provedor (webhook) e pagamentos confirmados.
-- ---------------------------------------------------------------------------
-- EVENTO_PROVEDOR e a caixa de entrada dos avisos do Asaas: o evento e gravado
-- antes de ser aplicado e aplicado uma vez so (ID_EVENTO_EXTERNO unico). Chega
-- sem escola; ESCOLA_ID e preenchido ao resolver a origem. O PAYLOAD ja vem
-- saneado (sem CPF/CNPJ, e-mail, telefone nem dado bancario).
CREATE TABLE EVENTO_PROVEDOR (
    ID_EVENTO           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ID_EVENTO_EXTERNO   VARCHAR(150) NOT NULL UNIQUE,
    TIPO_ORIGINAL       VARCHAR(60)  NOT NULL,
    TIPO                VARCHAR(30)  NOT NULL,
    REFERENCIA_EXTERNA  VARCHAR(60),
    ID_CONTA_EXTERNA    VARCHAR(64),
    ESCOLA_ID           UUID REFERENCES ESCOLA (ID_ESCOLA),
    PAYLOAD             JSONB NOT NULL,
    STATUS              VARCHAR(12) NOT NULL DEFAULT 'RECEBIDO'
                        CHECK (STATUS IN ('RECEBIDO', 'PROCESSANDO', 'PROCESSADO', 'IGNORADO', 'ERRO')),
    MOTIVO              VARCHAR(500),
    TENTATIVAS          INTEGER NOT NULL DEFAULT 0,
    RECEBIDO_EM         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PROCESSADO_EM       TIMESTAMPTZ
);
-- O agendador reprocessa o que ainda nao terminou.
CREATE INDEX evento_pendente_idx ON EVENTO_PROVEDOR (RECEBIDO_EM) WHERE STATUS IN ('RECEBIDO', 'ERRO', 'PROCESSANDO');

-- Pagamento confirmado pelo Asaas. No maximo um por cobranca.
CREATE TABLE PAGAMENTO_COBRANCA (
    ID_PAGAMENTO          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID             UUID NOT NULL,
    COBRANCA_ID           UUID NOT NULL UNIQUE,
    VALOR_PAGO            NUMERIC(10,2) NOT NULL CHECK (VALOR_PAGO > 0),
    VALOR_LIQUIDO         NUMERIC(10,2),
    DATA_PAGAMENTO        DATE NOT NULL,
    DATA_CREDITO          DATE,
    FORMA_PAGAMENTO       VARCHAR(6) NOT NULL CHECK (FORMA_PAGAMENTO IN ('PIX', 'BOLETO')),
    ID_TRANSACAO_EXTERNA  VARCHAR(100) NOT NULL,
    URL_RECIBO_PROVEDOR   VARCHAR(500),                     -- so auditoria; nunca sai do backend
    EVENTO_ID             UUID REFERENCES EVENTO_PROVEDOR (ID_EVENTO),  -- nulo se veio da reconciliacao
    REGISTRADO_EM         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pagamento_cobranca FOREIGN KEY (COBRANCA_ID, ESCOLA_ID)
        REFERENCES COBRANCA (ID_COBRANCA, ESCOLA_ID)
);
CREATE INDEX pagamento_escola_data_idx ON PAGAMENTO_COBRANCA (ESCOLA_ID, DATA_PAGAMENTO);
