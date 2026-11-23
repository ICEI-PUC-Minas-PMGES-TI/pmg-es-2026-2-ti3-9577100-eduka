package br.pucminas.eduka.backend.domain.auditoria;

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
    ESCOLA,
    /**
     * Periodo letivo (V53). Curso, disciplina, periodo e turma tem id numerico;
     * o REGISTRO_ID guarda o numero num UUID ({@code AuditoriaService#idNumerico}).
     */
    PERIODO_LETIVO,
    /** Curso (V53); id numerico. */
    CURSO,
    /** Disciplina (V53); id numerico. */
    DISCIPLINA,
    /** Turma, com a grade de disciplinas (V53); id numerico. */
    TURMA,
    /** Professor alocado numa disciplina da turma (V53). */
    ALOCACAO,
    /** Aula da grade semanal da turma (V53). */
    HORARIO,
    /** Aula registrada pelo professor, com conteudo e chamada (V53). */
    AULA,
    /** Presenca ou falta de um aluno numa aula: so a correcao da chamada (V53). */
    FREQUENCIA,
    /** Cadastro do responsavel e o vinculo dele com o aluno (V53). */
    RESPONSAVEL,
    /** Categoria de despesa (V53). */
    CATEGORIA_DESPESA,
    /** Pedido de correcao de dados feito pelo aluno (V53). */
    PEDIDO_CORRECAO
}
