package br.pucminas.eduka.backend.email;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Sem {@code BREVO_API_KEY} o backend sobe normalmente e usa
 * {@link LogEmailSender}: o fluxo de primeiro acesso roda inteiro, so o
 * e-mail nao sai. Basta preencher a chave para passar a enviar de verdade.
 */
@Configuration
@EnableConfigurationProperties({ BrevoProperties.class, NotificacaoProperties.class })
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    /**
     * Timeouts curtos: a tentativa imediata da fila de notificacoes acontece
     * depois do commit, na thread da requisicao, e um Brevo lento nao pode
     * segurar a resposta da API (ver {@link NotificacaoService}).
     */
    private static final Duration TIMEOUT_CONEXAO = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_LEITURA = Duration.ofSeconds(10);

    @Bean
    public EmailSender emailSender(BrevoProperties brevo) {
        if (!brevo.configurado()) {
            log.warn("BREVO_API_KEY nao configurada: e-mails serao apenas registrados no log");
            return new LogEmailSender();
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXAO).build());
        requestFactory.setReadTimeout(TIMEOUT_LEITURA);
        return new BrevoEmailSender(RestClient.builder().requestFactory(requestFactory), brevo);
    }
}
