package br.pucminas.eduka.backend.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API transacional do Brevo. O remetente precisa estar verificado na conta
 * do Brevo, senao a API recusa o envio.
 */
@ConfigurationProperties(prefix = "brevo")
public record BrevoProperties(
        String apiKey,
        String apiUrl,
        String remetenteEmail,
        String remetenteNome) {

    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Impede que a chave apareca em log. */
    @Override
    public String toString() {
        return "BrevoProperties[apiUrl=%s, remetenteEmail=%s, configurado=%s]"
                .formatted(apiUrl, remetenteEmail, configurado());
    }
}
