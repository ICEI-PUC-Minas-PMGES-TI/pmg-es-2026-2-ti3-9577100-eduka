-- ---------------------------------------------------------------------------
-- Aceite do aviso de privacidade (IU29, RNF05): quem aceitou qual versao e
-- quando. O usuario ve o aviso no primeiro acesso e de novo a cada versao
-- nova; o que ja aceitou a versao atual entra direto.
-- ---------------------------------------------------------------------------
-- Sem ESCOLA_ID: o SUPER_ADMIN tambem aceita, e ele nao tem escola. Um aceite
-- por usuario e versao; o historico das versoes anteriores fica, e e a prova
-- do que cada um aceitou.
CREATE TABLE ACEITE_PRIVACIDADE (
    ID_ACEITE_PRIVACIDADE UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    USUARIO_ID            UUID NOT NULL REFERENCES USUARIO(ID_USUARIO),
    VERSAO                VARCHAR(20) NOT NULL,
    ACEITO_EM             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_aceite_privacidade_usuario_versao UNIQUE (USUARIO_ID, VERSAO)
);
