-- ---------------------------------------------------------------------------
-- Recuperacao de senha por e-mail ("Esqueceu sua senha?").
-- ---------------------------------------------------------------------------
-- Quem esqueceu a senha informa o login e recebe no e-mail do cadastro um
-- codigo de 6 digitos de validade curta; com ele define uma senha nova (ver
-- RecuperacaoSenhaService). O codigo nunca e gravado: so o hash.
--
-- Uma linha por usuario. JANELA_INICIO abre a janela em que valem os limites
-- de envios (ENVIOS) e de codigos errados (TENTATIVAS); passada a janela, um
-- novo pedido zera os contadores na mesma linha. Apagar o usuario leva a linha
-- junto.
--
-- Versao 48, e nao 47: a V47 ja esta reservada pela branch do teste de carga.
CREATE TABLE RECUPERACAO_SENHA (
    ID_RECUPERACAO   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    USUARIO_ID       UUID NOT NULL UNIQUE REFERENCES USUARIO(ID_USUARIO) ON DELETE CASCADE,
    CODIGO_HASH      VARCHAR(255) NOT NULL,
    EXPIRA_EM        TIMESTAMPTZ NOT NULL,
    TENTATIVAS       INTEGER NOT NULL DEFAULT 0,
    ENVIOS           INTEGER NOT NULL DEFAULT 1,
    ULTIMO_ENVIO_EM  TIMESTAMPTZ NOT NULL,
    JANELA_INICIO    TIMESTAMPTZ NOT NULL,
    CRIADO_EM        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_recuperacao_senha_contadores CHECK (TENTATIVAS >= 0 AND ENVIOS >= 1)
);

-- E-mail com o codigo de recuperacao (referencia: usuario).
ALTER TABLE NOTIFICACAO DROP CONSTRAINT ck_notificacao_tipo;
ALTER TABLE NOTIFICACAO ADD CONSTRAINT ck_notificacao_tipo CHECK (TIPO IN ('ACESSO_INICIAL', 'CODIGO_LOGIN',
    'COBRANCA_CRIADA', 'VENCIMENTO_PROXIMO', 'PAGAMENTO_CONFIRMADO', 'AVISO_SUSPENSAO', 'ESCOLA_SUSPENSA',
    'RECUPERACAO_SENHA'));
