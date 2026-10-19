package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Item de {@code GET /v3/pix/addressKeys}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChavePixAsaas(String id, String type, String status) {
}
