package br.pucminas.eduka.backend.email;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Envia pela API transacional do Brevo ({@code POST /smtp/email}). A chave vai
 * no header {@code api-key}; erro HTTP vira excecao para quem chamou decidir o
 * que fazer.
 */
public class BrevoEmailSender implements EmailSender {

    private final RestClient restClient;
    private final BrevoProperties properties;

    public BrevoEmailSender(RestClient.Builder restClientBuilder, BrevoProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.apiUrl())
                .defaultHeader("api-key", properties.apiKey())
                .build();
    }

    @Override
    public void enviar(Email email) {
        EnvioBrevo corpo = new EnvioBrevo(
                new Contato(properties.remetenteEmail(), properties.remetenteNome()),
                List.of(new Contato(email.destinatario(), email.nomeDestinatario())),
                email.assunto(),
                email.html(),
                email.texto());

        restClient.post()
                .uri("/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toBodilessEntity();
    }

    record Contato(String email, String name) {
    }

    record EnvioBrevo(Contato sender, List<Contato> to, String subject, String htmlContent, String textContent) {
    }
}
