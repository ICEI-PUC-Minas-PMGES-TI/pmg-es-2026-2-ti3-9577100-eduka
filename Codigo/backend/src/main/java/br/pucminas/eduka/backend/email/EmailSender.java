package br.pucminas.eduka.backend.email;

/**
 * Envio de e-mail transacional. A implementacao e escolhida em
 * {@link EmailConfig}: Brevo quando ha chave configurada, log quando nao ha.
 */
public interface EmailSender {

    void enviar(Email email);
}
