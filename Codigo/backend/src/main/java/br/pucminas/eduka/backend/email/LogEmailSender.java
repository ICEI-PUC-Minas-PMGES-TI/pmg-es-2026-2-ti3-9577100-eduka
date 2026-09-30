package br.pucminas.eduka.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Usado enquanto nao ha {@code BREVO_API_KEY}: nada sai, so fica o registro
 * de que um e-mail teria sido enviado. Destinatario e corpo ficam fora do
 * log porque sao dado pessoal.
 */
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void enviar(Email email) {
        log.warn("BREVO_API_KEY nao configurada: e-mail \"{}\" nao foi enviado", email.assunto());
    }
}
