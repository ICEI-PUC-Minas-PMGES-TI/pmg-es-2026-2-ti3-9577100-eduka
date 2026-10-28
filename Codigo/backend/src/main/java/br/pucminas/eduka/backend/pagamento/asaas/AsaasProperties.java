package br.pucminas.eduka.backend.pagamento.asaas;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conta-pai do Eduka no Asaas e o webhook cadastrado em cada conta filha.
 *
 * <p>
 * Sem {@code api-key} o backend sobe, e as operacoes de pagamento respondem
 * 503. Sem {@code webhook-url}, {@code webhook-email} ou
 * {@code webhook-token}, a conta filha e criada sem webhook (o Asaas exige o
 * e-mail, achado A2 do spike) e a reconciliacao cobre os avisos.
 * </p>
 */
@ConfigurationProperties(prefix = "asaas")
public record AsaasProperties(
        String apiUrl,
        String apiKey,
        String webhookUrl,
        String webhookToken,
        String webhookEmail) {

    public boolean configurado() {
        return preenchido(apiKey);
    }

    /** Atalhos de Sandbox so funcionam contra a API do Sandbox. */
    public boolean sandbox() {
        return apiUrl != null && apiUrl.contains("sandbox");
    }

    public boolean webhookConfigurado() {
        return preenchido(webhookUrl) && preenchido(webhookEmail) && preenchido(webhookToken);
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** Impede que a chave e o token do webhook aparecam em log. */
    @Override
    public String toString() {
        return "AsaasProperties[apiUrl=%s, configurado=%s, webhookUrl=%s, webhookConfigurado=%s]"
                .formatted(apiUrl, configurado(), webhookUrl, webhookConfigurado());
    }
}
