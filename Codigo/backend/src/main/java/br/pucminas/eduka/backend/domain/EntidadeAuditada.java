package br.pucminas.eduka.backend.domain;

/**
 * Tipo do registro afetado (coluna {@code LOG_AUDITORIA.ENTIDADE}, V22, V34).
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
    FATURA_PLATAFORMA
}
