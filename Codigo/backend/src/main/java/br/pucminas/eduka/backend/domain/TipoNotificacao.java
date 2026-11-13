package br.pucminas.eduka.backend.domain;

/**
 * O que a notificacao avisa; diz tambem a que tabela {@code REFERENCIA_ID}
 * aponta. Os valores precisam bater com o CHECK da V29 (refeito na V43 e na V48).
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
    PAGAMENTO_CONFIRMADO,
    /** Referencia: fatura da plataforma. Ao MASTER: a escola sera suspensa se a fatura nao for paga (V43). */
    AVISO_SUSPENSAO,
    /** Referencia: fatura da plataforma. Ao MASTER: a escola foi suspensa por causa da fatura (V43). */
    ESCOLA_SUSPENSA,
    /** Referencia: usuario. Codigo para definir uma senha nova (V48). */
    RECUPERACAO_SENHA
}
