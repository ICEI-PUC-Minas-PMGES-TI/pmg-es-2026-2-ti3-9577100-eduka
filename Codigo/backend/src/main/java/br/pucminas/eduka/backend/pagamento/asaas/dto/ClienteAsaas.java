package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Cliente devolvido pelo Asaas; so o id interessa. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClienteAsaas(String id) {
}
