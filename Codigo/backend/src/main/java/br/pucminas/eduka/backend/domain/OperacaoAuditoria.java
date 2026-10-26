package br.pucminas.eduka.backend.domain;

/**
 * O que foi feito no registro auditado (coluna {@code LOG_AUDITORIA.OPERACAO},
 * V22).
 */
public enum OperacaoAuditoria {
    CRIAR,
    EDITAR,
    CANCELAR,
    /** Baixa manual de pagamento (V24). */
    PAGAR,
    /** Pagamento confirmado pelo provedor, sem autor (V31). */
    CONFIRMAR_PAGAMENTO,
    /** Desfazer um pagamento ja registrado. */
    ESTORNAR
}
