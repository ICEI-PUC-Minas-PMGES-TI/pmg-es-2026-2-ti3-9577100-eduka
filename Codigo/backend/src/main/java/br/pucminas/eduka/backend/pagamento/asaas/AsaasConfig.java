package br.pucminas.eduka.backend.pagamento.asaas;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.pucminas.eduka.backend.pagamento.PaymentProvider;

/**
 * Liga o {@link PaymentProvider} ao Asaas. Sem {@code ASAAS_API_KEY} o
 * backend sobe do mesmo jeito; cada operacao de pagamento responde 503
 * {@code PROVEDOR_NAO_CONFIGURADO}.
 */
@Configuration
@EnableConfigurationProperties(AsaasProperties.class)
public class AsaasConfig {

    private static final Logger log = LoggerFactory.getLogger(AsaasConfig.class);

    /**
     * Mesmos timeouts do {@code EmailConfig}. Estourar o tempo de leitura nao
     * diz se o Asaas aplicou o pedido: o adaptador trata como inconclusivo.
     */
    private static final Duration TIMEOUT_CONEXAO = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_LEITURA = Duration.ofSeconds(10);

    @Bean
    public PaymentProvider paymentProvider(AsaasProperties asaas) {
        if (!asaas.configurado()) {
            log.warn("ASAAS_API_KEY nao configurada: operacoes de pagamento responderao 503");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXAO).build());
        requestFactory.setReadTimeout(TIMEOUT_LEITURA);
        return new AsaasAdapter(RestClient.builder().requestFactory(requestFactory), asaas);
    }
}
