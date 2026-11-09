-- Auditoria ampliada para alem do financeiro: equipe, alunos, professores,
-- matriculas, avaliacoes e notas, e a escola (dados, plano e situacao).
--
-- ORIGEM ganha SISTEMA, para o que o Eduka faz sozinho (ex.: suspender a
-- escola por fatura em atraso): como o PROVEDOR, sem autor.

ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_operacao;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_operacao
    CHECK (OPERACAO IN ('CRIAR', 'EDITAR', 'CANCELAR', 'PAGAR', 'CONFIRMAR_PAGAMENTO', 'PAGO_APOS_CANCELAR',
                        'ESTORNAR', 'CONTESTAR', 'ATIVAR', 'ALTERAR_SITUACAO', 'INATIVAR', 'EXCLUIR',
                        'REENVIAR_ACESSO', 'PUBLICAR_NOTAS', 'SUSPENDER'));

ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_entidade;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_entidade
    CHECK (ENTIDADE IN ('COBRANCA', 'PAGAMENTO', 'DESPESA', 'CONTA_RECEBIMENTO', 'FATURA_PLATAFORMA', 'USUARIO',
                        'ALUNO', 'PROFESSOR', 'MATRICULA', 'AVALIACAO', 'NOTA', 'ESCOLA'));

ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_origem;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_origem
    CHECK (ORIGEM IN ('USUARIO', 'PROVEDOR', 'SISTEMA'));
