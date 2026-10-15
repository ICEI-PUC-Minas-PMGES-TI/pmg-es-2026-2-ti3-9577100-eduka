package br.pucminas.eduka.backend.domain;

/**
 * O que foi feito no registro auditado (coluna {@code LOG_AUDITORIA.OPERACAO},
 * V22).
 */
public enum OperacaoAuditoria {
    CRIAR,
    EDITAR,
    CANCELAR,
    /** Desfazer um pagamento ja registrado. */
    ESTORNAR
}
