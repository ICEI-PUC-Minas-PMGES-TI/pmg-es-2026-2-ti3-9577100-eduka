-- Auditoria ampliada (RF22, RNF17): estrutura academica (periodo letivo, curso,
-- disciplina e turma), alocacoes e horario, aulas e chamada, responsaveis,
-- categorias de despesa e pedidos de correcao.
--
-- Periodo letivo, curso, disciplina e turma tem id numerico (BIGINT): o
-- REGISTRO_ID continua UUID e guarda o numero nos bits baixos
-- (00000000-0000-0000-0000-00000000002a e o id 42; ver
-- AuditoriaService.idNumerico).

ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_operacao;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_operacao
    CHECK (OPERACAO IN ('CRIAR', 'EDITAR', 'CANCELAR', 'PAGAR', 'CONFIRMAR_PAGAMENTO', 'PAGO_APOS_CANCELAR',
                        'ESTORNAR', 'CONTESTAR', 'ATIVAR', 'ALTERAR_SITUACAO', 'INATIVAR', 'EXCLUIR',
                        'REENVIAR_ACESSO', 'PUBLICAR_NOTAS', 'SUSPENDER', 'EXPORTAR_DADOS', 'ANONIMIZAR',
                        'ENCERRAR', 'ABRIR_MATRICULAS', 'VINCULAR', 'DESVINCULAR', 'ATENDER'));

ALTER TABLE LOG_AUDITORIA DROP CONSTRAINT ck_log_auditoria_entidade;
ALTER TABLE LOG_AUDITORIA ADD CONSTRAINT ck_log_auditoria_entidade
    CHECK (ENTIDADE IN ('COBRANCA', 'PAGAMENTO', 'DESPESA', 'CONTA_RECEBIMENTO', 'FATURA_PLATAFORMA', 'USUARIO',
                        'ALUNO', 'PROFESSOR', 'MATRICULA', 'AVALIACAO', 'NOTA', 'ESCOLA',
                        'PERIODO_LETIVO', 'CURSO', 'DISCIPLINA', 'TURMA', 'ALOCACAO', 'HORARIO', 'AULA',
                        'FREQUENCIA', 'RESPONSAVEL', 'CATEGORIA_DESPESA', 'PEDIDO_CORRECAO'));
