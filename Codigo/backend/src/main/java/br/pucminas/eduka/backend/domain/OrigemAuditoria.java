package br.pucminas.eduka.backend.domain;

/**
 * Quem originou a linha do log (coluna {@code LOG_AUDITORIA.ORIGEM}, V32).
 */
public enum OrigemAuditoria {
    /** Usuario autenticado; a linha sempre tem autor. */
    USUARIO,
    /** Aviso ou consulta do provedor de pagamento; a linha nao tem autor. */
    PROVEDOR
}
