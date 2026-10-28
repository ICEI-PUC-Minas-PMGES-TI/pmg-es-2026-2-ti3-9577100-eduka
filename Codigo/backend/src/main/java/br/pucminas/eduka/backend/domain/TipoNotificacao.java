package br.pucminas.eduka.backend.domain;

/**
 * O que a notificacao avisa; diz tambem a que tabela {@code REFERENCIA_ID}
 * aponta. Os valores precisam bater com o CHECK da V29.
 */
public enum TipoNotificacao {
    /** Referencia: usuario. */
    ACESSO_INICIAL,
    /** Referencia: usuario. */
    CODIGO_LOGIN,
    /** Referencia: cobranca. */
    COBRANCA_CRIADA,
    /** Referencia: cobranca. */
    VENCIMENTO_PROXIMO,
    /** Referencia: cobranca. */
    PAGAMENTO_CONFIRMADO
}
