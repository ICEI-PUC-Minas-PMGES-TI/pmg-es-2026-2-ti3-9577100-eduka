package br.pucminas.eduka.backend.domain;

/**
 * Tipo do registro afetado (coluna {@code LOG_AUDITORIA.ENTIDADE}, V22). Junto
 * com {@code REGISTRO_ID}, identifica a linha que sofreu a operacao.
 *
 * <p>
 * PAGAMENTO e DESPESA ja estao previstos no banco; os modulos ainda nao
 * existem e devem chamar {@link br.pucminas.eduka.backend.service.AuditoriaService}
 * quando forem criados.
 * </p>
 */
public enum EntidadeAuditada {
    COBRANCA,
    PAGAMENTO,
    DESPESA
}
