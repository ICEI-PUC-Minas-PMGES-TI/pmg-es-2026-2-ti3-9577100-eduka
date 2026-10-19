package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code GET /v3/myAccount/status} e o {@code accountStatus} dos eventos de conta. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SituacaoContaAsaas(String general, String documentation, String commercialInfo,
        String bankAccountInfo) {
}
