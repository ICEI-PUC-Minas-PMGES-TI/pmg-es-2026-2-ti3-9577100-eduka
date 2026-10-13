-- ---------------------------------------------------------------------------
-- "Em atraso" passa a ser gravado em COBRANCA.STATUS.
-- ---------------------------------------------------------------------------
-- Na V20 o atraso era so calculado na leitura (PENDENTE com vencimento no
-- passado). Agora uma rotina diaria (CobrancaAtrasoRotina) grava EM_ATRASO, e
-- as mudancas de situacao permitidas ficam em StatusCobranca.
ALTER TABLE COBRANCA DROP CONSTRAINT ck_cobranca_status;
ALTER TABLE COBRANCA ADD CONSTRAINT ck_cobranca_status
    CHECK (STATUS IN ('PENDENTE', 'EM_ATRASO', 'PAGA', 'CANCELADA'));

-- Cobrancas que ja estavam vencidas antes da rotina existir. "Hoje" e o da
-- escola (mesmo fuso de CobrancaService), nao o de UTC.
UPDATE COBRANCA
   SET STATUS = 'EM_ATRASO',
       ATUALIZADO_EM = CURRENT_TIMESTAMP
 WHERE STATUS = 'PENDENTE'
   AND DATA_VENCIMENTO < (CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')::date;

-- A rotina varre as pendentes vencidas de todas as escolas de uma vez.
CREATE INDEX cobranca_pendente_vencimento_idx ON COBRANCA (DATA_VENCIMENTO) WHERE STATUS = 'PENDENTE';
