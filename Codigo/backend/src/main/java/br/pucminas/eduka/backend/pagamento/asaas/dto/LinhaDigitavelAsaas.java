package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code GET /v3/payments/{id}/identificationField}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinhaDigitavelAsaas(String identificationField, String barCode, String nossoNumero) {
}
