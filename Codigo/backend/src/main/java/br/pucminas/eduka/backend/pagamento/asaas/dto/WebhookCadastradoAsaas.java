package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Item de {@code GET /v3/webhooks}: so o que basta para achar o webhook pela URL. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookCadastradoAsaas(String id, String url) {
}
