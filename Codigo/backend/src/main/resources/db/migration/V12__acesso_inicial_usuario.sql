-- ---------------------------------------------------------------------------
-- Primeiro acesso por e-mail.
-- ---------------------------------------------------------------------------
-- O usuario nasce com senha provisoria (o CPF, hoje) e o login com ela nao
-- emite token: so permite trocar por uma senha definitiva (ver AuthService).
-- A senha provisoria expira; depois disso so um novo envio pela escola.
ALTER TABLE USUARIO
    ADD COLUMN SENHA_PROVISORIA            BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN SENHA_PROVISORIA_EXPIRA_EM  TIMESTAMPTZ;

-- Login do professor. Nulo enquanto o acesso nao foi criado.
ALTER TABLE PROFESSOR
    ADD COLUMN USUARIO_ID UUID UNIQUE REFERENCES USUARIO(ID_USUARIO);
