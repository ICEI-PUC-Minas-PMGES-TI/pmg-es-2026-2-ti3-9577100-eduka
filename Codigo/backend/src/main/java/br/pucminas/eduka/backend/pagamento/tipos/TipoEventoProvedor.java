package br.pucminas.eduka.backend.pagamento.tipos;

/** Avisos do provedor que o Eduka trata. O resto chega como {@code NAO_TRATADO}. */
public enum TipoEventoProvedor {
    COBRANCA_CRIADA,
    PAGAMENTO_CONFIRMADO,
    PAGAMENTO_RECEBIDO,
    COBRANCA_REMOVIDA,
    SITUACAO_CONTA_ALTERADA,
    NAO_TRATADO
}
