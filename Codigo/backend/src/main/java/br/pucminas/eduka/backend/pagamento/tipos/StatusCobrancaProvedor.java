package br.pucminas.eduka.backend.pagamento.tipos;

/**
 * Situacao da cobranca no provedor. {@code OUTRO} cobre estorno, chargeback,
 * analise de risco e qualquer status que o Eduka nao trata.
 */
public enum StatusCobrancaProvedor {
    AGUARDANDO_PAGAMENTO,
    VENCIDA,
    CONFIRMADA,
    RECEBIDA,
    REMOVIDA,
    OUTRO
}
