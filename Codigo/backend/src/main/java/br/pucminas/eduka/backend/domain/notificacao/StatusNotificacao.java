package br.pucminas.eduka.backend.domain.notificacao;

/** Situacao de uma {@link Notificacao}. So PENDENTE ainda pode ser enviada. */
public enum StatusNotificacao {
    /** Na fila: aguardando a primeira tentativa ou uma nova. */
    PENDENTE,
    ENVIADA,
    /** Esgotou as tentativas ou o provedor recusou de vez (ex.: e-mail invalido). */
    FALHOU,
    /** Passou de {@code validaAte} antes de sair (ex.: codigo de login vencido). */
    EXPIRADA
}
