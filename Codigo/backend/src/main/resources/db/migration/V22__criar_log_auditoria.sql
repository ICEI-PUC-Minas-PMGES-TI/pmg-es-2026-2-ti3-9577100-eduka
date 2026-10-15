-- ---------------------------------------------------------------------------
-- Trilha de auditoria das operacoes financeiras (RNF05).
-- ---------------------------------------------------------------------------
-- Cada linha diz quem (USUARIO_ID) fez o que (OPERACAO) em qual registro
-- (ENTIDADE + REGISTRO_ID) e quando (DATA_HORA). E gravada na mesma transacao
-- da operacao (ver AuditoriaService): se a operacao falha, o log some junto.
--
-- REGISTRO_ID nao tem FK porque aponta para tabelas diferentes conforme a
-- ENTIDADE. ESCOLA_ID e opcional para servir tambem a acoes de plataforma do
-- SUPER_ADMIN, que nao pertence a nenhuma escola.
--
-- O log so recebe INSERT: a aplicacao nunca edita nem apaga uma linha. Nada
-- cascateia, para que apagar escola ou usuario nao leve o historico junto.
CREATE TABLE LOG_AUDITORIA (
    ID_LOG_AUDITORIA  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID         UUID REFERENCES ESCOLA(ID_ESCOLA),
    USUARIO_ID        UUID NOT NULL REFERENCES USUARIO(ID_USUARIO),
    OPERACAO          VARCHAR(20) NOT NULL,
    ENTIDADE          VARCHAR(60) NOT NULL,
    REGISTRO_ID       UUID NOT NULL,
    DATA_HORA         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_log_auditoria_operacao CHECK (OPERACAO IN ('CRIAR', 'EDITAR', 'CANCELAR', 'ESTORNAR')),
    CONSTRAINT ck_log_auditoria_entidade CHECK (ENTIDADE IN ('COBRANCA', 'PAGAMENTO', 'DESPESA'))
);

-- Consultas tipicas: o historico de um registro e a trilha da escola por data.
CREATE INDEX log_auditoria_registro_idx ON LOG_AUDITORIA (ENTIDADE, REGISTRO_ID);
CREATE INDEX log_auditoria_escola_data_idx ON LOG_AUDITORIA (ESCOLA_ID, DATA_HORA);
CREATE INDEX log_auditoria_usuario_id_idx ON LOG_AUDITORIA (USUARIO_ID);
