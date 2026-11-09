package br.pucminas.eduka.backend.domain;

/**
 * Tipo do registro afetado (coluna {@code LOG_AUDITORIA.ENTIDADE}, V22, V34, V44).
 * Junto com {@code REGISTRO_ID}, identifica a linha que sofreu a operacao.
 *
 * <p>
 * PAGAMENTO ja esta previsto no banco, mas ainda nenhum modulo o usa: o
 * pagamento da cobranca e auditado na propria cobranca.
 * </p>
 */
public enum EntidadeAuditada {
    COBRANCA,
    PAGAMENTO,
    DESPESA,
    /** Conta de recebimento da escola no provedor (V34). */
    CONTA_RECEBIMENTO,
    /** Fatura que a escola paga ao Eduka (V34). */
    FATURA_PLATAFORMA,
    /** Login da equipe (secretaria e financeiro); o registro e o USUARIO (V44). */
    USUARIO,
    /** Cadastro do aluno (V44). */
    ALUNO,
    /** Cadastro do professor (V44). */
    PROFESSOR,
    /** Matricula do aluno numa turma (V44). */
    MATRICULA,
    /** Avaliacao de uma disciplina numa turma (V44). */
    AVALIACAO,
    /** Nota de um aluno numa avaliacao (V44). */
    NOTA,
    /** A escola: dados, plano, assinatura e situacao (V44). */
    ESCOLA
}
