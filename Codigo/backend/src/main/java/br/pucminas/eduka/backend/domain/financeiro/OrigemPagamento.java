package br.pucminas.eduka.backend.domain.financeiro;

/**
 * De onde veio o pagamento da cobranca (coluna {@code ORIGEM} de
 * {@code PAGAMENTO_COBRANCA}, V28).
 */
public enum OrigemPagamento {
    /** Confirmado pelo provedor de pagamento. */
    ONLINE,
    /** Baixa manual feita pela escola. */
    MANUAL
}
