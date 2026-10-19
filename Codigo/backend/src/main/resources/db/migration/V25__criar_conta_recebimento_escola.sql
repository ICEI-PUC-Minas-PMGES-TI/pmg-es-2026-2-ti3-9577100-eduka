-- ---------------------------------------------------------------------------
-- Conta de recebimento da escola: a conta filha (subconta) dela no Asaas.
-- ---------------------------------------------------------------------------
-- Uma por escola. Dados bancarios NAO sao guardados: so ficam o codigo do banco
-- e os 4 ultimos digitos da conta, para exibir. A chave da conta filha fica
-- cifrada (AES-256-GCM, PiiCryptoService) e so o roteador a decifra.
--
-- STATUS acompanha a criacao no Asaas (CRIANDO, VERIFICACAO_PENDENTE,
-- ERRO_CRIACAO, CREDENCIAL_INDISPONIVEL) e depois a aprovacao
-- (AGUARDANDO_APROVACAO, APROVADA, REJEITADA). As quatro SITUACAO_* sao as
-- dimensoes do cadastro no Asaas.
CREATE TABLE CONTA_RECEBIMENTO_ESCOLA (
    ID_CONTA_RECEBIMENTO      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID                 UUID NOT NULL UNIQUE REFERENCES ESCOLA (ID_ESCOLA),
    STATUS                    VARCHAR(30) NOT NULL CHECK (STATUS IN ('CRIANDO', 'VERIFICACAO_PENDENTE',
                                  'ERRO_CRIACAO', 'AGUARDANDO_APROVACAO', 'APROVADA', 'REJEITADA',
                                  'CREDENCIAL_INDISPONIVEL')),
    RAZAO_SOCIAL              VARCHAR(150) NOT NULL,
    EMAIL_FINANCEIRO          VARCHAR(150) NOT NULL,
    TELEFONE_CELULAR          VARCHAR(11)  NOT NULL,
    TIPO_EMPRESA              VARCHAR(20)  NOT NULL CHECK (TIPO_EMPRESA IN ('MEI', 'LIMITED', 'INDIVIDUAL', 'ASSOCIATION')),
    FATURAMENTO_MENSAL        NUMERIC(14,2) NOT NULL CHECK (FATURAMENTO_MENSAL > 0),
    LOGRADOURO                VARCHAR(200) NOT NULL,
    NUMERO                    VARCHAR(10)  NOT NULL,
    COMPLEMENTO               VARCHAR(100),
    BAIRRO                    VARCHAR(100) NOT NULL,
    CEP                       CHAR(8)      NOT NULL,
    BANCO_CODIGO              CHAR(3)      NOT NULL,
    CONTA_FINAL               CHAR(4)      NOT NULL,
    ID_CONTA_EXTERNA          VARCHAR(64) UNIQUE,
    WALLET_ID                 VARCHAR(64) UNIQUE,
    CREDENCIAL_CIFRADA        BYTEA,
    SITUACAO_GERAL            VARCHAR(12) CHECK (SITUACAO_GERAL            IN ('PENDENTE', 'EM_ANALISE', 'APROVADO', 'REJEITADO')),
    SITUACAO_DOCUMENTACAO     VARCHAR(12) CHECK (SITUACAO_DOCUMENTACAO     IN ('PENDENTE', 'EM_ANALISE', 'APROVADO', 'REJEITADO')),
    SITUACAO_DADOS_COMERCIAIS VARCHAR(12) CHECK (SITUACAO_DADOS_COMERCIAIS IN ('PENDENTE', 'EM_ANALISE', 'APROVADO', 'REJEITADO')),
    SITUACAO_CONTA_BANCARIA   VARCHAR(12) CHECK (SITUACAO_CONTA_BANCARIA   IN ('PENDENTE', 'EM_ANALISE', 'APROVADO', 'REJEITADO')),
    ULTIMO_ERRO               VARCHAR(500),
    SOLICITADA_EM             TIMESTAMPTZ NOT NULL,
    APROVADA_EM               TIMESTAMPTZ,
    SINCRONIZADA_EM           TIMESTAMPTZ,
    CRIADO_EM                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM             TIMESTAMPTZ,
    VERSAO                    BIGINT NOT NULL DEFAULT 0,
    -- Depois de criada no Asaas, a conta sempre tem id externo e credencial.
    CONSTRAINT ck_conta_credencial CHECK (STATUS NOT IN ('AGUARDANDO_APROVACAO', 'APROVADA', 'REJEITADA')
                                          OR (ID_CONTA_EXTERNA IS NOT NULL AND CREDENCIAL_CIFRADA IS NOT NULL))
);

-- O agendador procura contas por status (ex.: AGUARDANDO_APROVACAO).
CREATE INDEX conta_recebimento_status_idx ON CONTA_RECEBIMENTO_ESCOLA (STATUS);
