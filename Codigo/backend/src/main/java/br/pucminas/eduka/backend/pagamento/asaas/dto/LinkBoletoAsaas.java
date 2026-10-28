package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * So o link do PDF do boleto de {@code GET /v3/payments/{id}}. Fica fora de
 * {@link CobrancaAsaas} de proposito: o link so e lido na hora de baixar o PDF
 * e nunca sai do adaptador.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinkBoletoAsaas(String bankSlipUrl) {
}
