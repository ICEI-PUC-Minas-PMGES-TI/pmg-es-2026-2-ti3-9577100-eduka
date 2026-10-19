package br.pucminas.eduka.backend.email;

import java.util.List;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Envia pela API transacional do Brevo ({@code POST /smtp/email}). A chave vai
 * no header {@code api-key}; erro HTTP vira excecao para quem chamou decidir o
 * que fazer.
 *
 * <p>
 * Erro 4xx que nao se resolve tentando de novo (endereco invalido, corpo
 * recusado) vira {@link EmailRecusadoException}. 401/403 (chave errada ou sem
 * permissao), 408 e 429 continuam como falha temporaria: corrigida a
 * configuracao ou passado o limite, a nova tentativa sai.
 * </p>
 */
public class BrevoEmailSender implements EmailSender {

    /** 4xx que vale tentar de novo. */
    private static final Set<Integer> CLIENTE_TEMPORARIO = Set.of(401, 403, 408, 429);

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

        try {
            restClient.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            int status = ex.getStatusCode().value();
            if (CLIENTE_TEMPORARIO.contains(status)) {
                throw ex;
            }
            throw new EmailRecusadoException(status);
        }
    }

    record Contato(String email, String name) {
    }

    record EnvioBrevo(Contato sender, List<Contato> to, String subject, String htmlContent, String textContent) {
    }
}
