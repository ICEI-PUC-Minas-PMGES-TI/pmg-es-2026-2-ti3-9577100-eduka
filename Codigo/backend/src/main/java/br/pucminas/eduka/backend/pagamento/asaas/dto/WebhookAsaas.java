package br.pucminas.eduka.backend.pagamento.asaas.dto;

import java.util.List;

/** Webhook cadastrado junto com a conta filha. */
public record WebhookAsaas(
        String name,
        String url,
        String email,
        boolean enabled,
        boolean interrupted,
        String authToken,
        String sendType,
        List<String> events) {

    @Override
    public String toString() {
        return "WebhookAsaas[url=%s, events=%d]".formatted(url, events == null ? 0 : events.size());
    }
}
