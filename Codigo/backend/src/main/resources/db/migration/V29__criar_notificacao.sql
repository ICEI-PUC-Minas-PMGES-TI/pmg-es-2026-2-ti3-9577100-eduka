-- ---------------------------------------------------------------------------
-- Fila de notificacoes (e-mail) com novas tentativas.
-- ---------------------------------------------------------------------------
-- A regra de negocio grava a notificacao na mesma transacao da operacao (se a
-- operacao falha, a notificacao some junto) e o envio acontece depois, fora
-- dela: uma tentativa logo apos o commit e, se falhar, NotificacaoRotina
-- tenta de novo com espera crescente ate o maximo de tentativas.
--
-- Destinatario, assunto e corpo sao dado pessoal (o corpo do codigo de login
-- traz o codigo): ficam em BYTEA, cifrados pela aplicacao como em PESSOA (V9).
-- O corpo e apagado quando a notificacao chega a um estado final; fica so o
-- registro de que foi (ou nao) enviada.
--
-- VALIDA_ATE: depois disso nao adianta mais enviar (codigo de login vencido,
-- senha provisoria expirada). A notificacao vira EXPIRADA sem sair.
--
-- REFERENCIA_ID: o registro que originou o aviso (usuario, cobranca), para
-- rastrear quem deixou de receber. Sem FK: aponta para tabelas diferentes
-- conforme o TIPO.
CREATE TABLE NOTIFICACAO (
    ID_NOTIFICACAO        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    TIPO                  VARCHAR(40) NOT NULL,
    REFERENCIA_ID         UUID,
    DESTINATARIO          BYTEA NOT NULL,
    NOME_DESTINATARIO     BYTEA,
    ASSUNTO               BYTEA NOT NULL,
    HTML                  BYTEA,
    TEXTO                 BYTEA,
    CHAVE_VERSAO          SMALLINT NOT NULL DEFAULT 1,
    STATUS                VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    TENTATIVAS            INTEGER NOT NULL DEFAULT 0,
    PROXIMA_TENTATIVA_EM  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    VALIDA_ATE            TIMESTAMPTZ,
    ULTIMO_ERRO           VARCHAR(255),
    CRIADO_EM             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ENVIADO_EM            TIMESTAMPTZ,
    ATUALIZADO_EM         TIMESTAMPTZ,
    CONSTRAINT ck_notificacao_status CHECK (STATUS IN ('PENDENTE', 'ENVIADA', 'FALHOU', 'EXPIRADA')),
    CONSTRAINT ck_notificacao_tipo CHECK (TIPO IN ('ACESSO_INICIAL', 'CODIGO_LOGIN', 'COBRANCA_CRIADA',
        'VENCIMENTO_PROXIMO', 'PAGAMENTO_CONFIRMADO')),
    CONSTRAINT ck_notificacao_tentativas CHECK (TENTATIVAS >= 0)
);

-- A rotina so olha as pendentes que ja podem ser tentadas.
CREATE INDEX notificacao_pendente_idx ON NOTIFICACAO (PROXIMA_TENTATIVA_EM) WHERE STATUS = 'PENDENTE';
CREATE INDEX notificacao_referencia_idx ON NOTIFICACAO (REFERENCIA_ID);
