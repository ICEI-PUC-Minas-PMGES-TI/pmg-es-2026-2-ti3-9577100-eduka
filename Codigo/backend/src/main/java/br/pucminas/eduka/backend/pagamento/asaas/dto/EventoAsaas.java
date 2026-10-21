package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corpo do webhook. {@code account.id} e a conta que gerou o evento (a filha,
 * no Fluxo B; achado A14). {@code accountStatus} e a foto atual da conta, nao
 * o estado no momento do evento (achado A11).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoAsaas(
        String id,
        String event,
        String dateCreated,
        Conta account,
        CobrancaAsaas payment,
        SituacaoContaAsaas accountStatus) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Conta(String id, String ownerId) {
    }
}
