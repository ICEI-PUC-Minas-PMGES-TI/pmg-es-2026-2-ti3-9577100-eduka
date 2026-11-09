-- LGPD: exportar os dados pessoais de um aluno a pedido e anonimizar o aluno
-- que saiu da escola.
--
-- ANONIMIZADO_EM marca o aluno cujo dado pessoal foi apagado: a linha fica,
-- porque cobrancas, pagamentos, matriculas e notas apontam para ela e o
-- historico financeiro tem que ser guardado por lei.

ALTER TABLE alunos ADD COLUMN anonimizado_em TIMESTAMPTZ;

ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_operacao;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_operacao
    CHECK (OPERACAO IN ('CRIAR', 'EDITAR', 'CANCELAR', 'PAGAR', 'CONFIRMAR_PAGAMENTO', 'PAGO_APOS_CANCELAR',
                        'ESTORNAR', 'CONTESTAR', 'ATIVAR', 'ALTERAR_SITUACAO', 'INATIVAR', 'EXCLUIR',
                        'REENVIAR_ACESSO', 'PUBLICAR_NOTAS', 'SUSPENDER', 'EXPORTAR_DADOS', 'ANONIMIZAR'));
