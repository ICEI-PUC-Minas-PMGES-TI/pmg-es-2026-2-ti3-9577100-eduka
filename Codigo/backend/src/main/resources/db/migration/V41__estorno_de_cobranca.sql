-- ---------------------------------------------------------------------------
-- Estorno de cobranca: o valor devolvido sai do recebido, no periodo do estorno.
-- ---------------------------------------------------------------------------
-- Decisao do grupo: a cobranca estornada continua PAGA (encerrada). Para cobrar
-- de novo, a escola lanca outra cobranca. Por isso nao ha situacao nova.
--
-- Um pagamento pode ter varios estornos parciais, cada um na sua data, entao
-- cada um vira uma linha. PAGAMENTO_COBRANCA.VALOR_ESTORNADO guarda a soma, para
-- as telas e o resumo dos relatorios nao somarem a tabela a cada leitura. Os
-- dois sao gravados na mesma transacao, com o pagamento travado.
ALTER TABLE PAGAMENTO_COBRANCA ADD COLUMN VALOR_ESTORNADO NUMERIC(10,2) NOT NULL DEFAULT 0;

ALTER TABLE PAGAMENTO_COBRANCA ADD CONSTRAINT ck_pagamento_valor_estornado
    CHECK (VALOR_ESTORNADO >= 0 AND VALOR_ESTORNADO <= VALOR_PAGO);

CREATE TABLE ESTORNO_COBRANCA (
    ID_ESTORNO    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID     UUID NOT NULL,
    COBRANCA_ID   UUID NOT NULL,
    VALOR         NUMERIC(10,2) NOT NULL CHECK (VALOR > 0),
    ESTORNADO_EM  TIMESTAMPTZ NOT NULL,
    EVENTO_ID     UUID REFERENCES EVENTO_PROVEDOR (ID_EVENTO),
    REGISTRADO_EM TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_estorno_cobranca FOREIGN KEY (COBRANCA_ID, ESCOLA_ID)
        REFERENCES COBRANCA (ID_COBRANCA, ESCOLA_ID)
);

CREATE INDEX estorno_escola_data_idx ON ESTORNO_COBRANCA (ESCOLA_ID, ESTORNADO_EM);
CREATE INDEX estorno_cobranca_id_idx ON ESTORNO_COBRANCA (COBRANCA_ID);
