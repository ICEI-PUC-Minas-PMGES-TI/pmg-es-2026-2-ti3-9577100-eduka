-- ---------------------------------------------------------------------------
-- Verificacao em duas etapas do login.
-- ---------------------------------------------------------------------------
-- Depois de a senha conferir, o backend manda um codigo de 6 digitos por
-- e-mail e so emite os tokens quando ele e confirmado (ver
-- VerificacaoLoginService). O codigo nunca e gravado: so o hash.
--
-- O ID e o que o front devolve junto com o codigo; por isso e aleatorio e nao
-- sequencial. Cada usuario tem no maximo uma verificacao aberta: um novo login
-- apaga a anterior. Apagar o usuario leva a verificacao junto.
CREATE TABLE VERIFICACAO_LOGIN (
    ID_VERIFICACAO   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    USUARIO_ID       UUID NOT NULL REFERENCES USUARIO(ID_USUARIO) ON DELETE CASCADE,
    CODIGO_HASH      VARCHAR(255) NOT NULL,
    EXPIRA_EM        TIMESTAMPTZ NOT NULL,
    TENTATIVAS       INTEGER NOT NULL DEFAULT 0,
    ENVIOS           INTEGER NOT NULL DEFAULT 1,
    ULTIMO_ENVIO_EM  TIMESTAMPTZ NOT NULL,
    CRIADO_EM        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX verificacao_login_usuario_id_idx ON VERIFICACAO_LOGIN (USUARIO_ID);
