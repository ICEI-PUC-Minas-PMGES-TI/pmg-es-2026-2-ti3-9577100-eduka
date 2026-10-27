package br.pucminas.eduka.backend.pagamento.tipos;

/** Avisos do provedor que o Eduka trata. O resto chega como {@code NAO_TRATADO}. */
public enum TipoEventoProvedor {
    COBRANCA_CRIADA,
    PAGAMENTO_CONFIRMADO,
    PAGAMENTO_RECEBIDO,
    COBRANCA_REMOVIDA,
    SITUACAO_CONTA_ALTERADA,
    /** Pagamento estornado no provedor, total ou parcialmente. */
    ESTORNO,
    /** Contestacao (chargeback) do pagamento no provedor, em qualquer etapa. */
    CONTESTACAO,
    NAO_TRATADO
}
