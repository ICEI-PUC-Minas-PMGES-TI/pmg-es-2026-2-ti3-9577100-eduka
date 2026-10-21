package br.pucminas.eduka.backend.pagamento.asaas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Corpo de erro do Asaas: {@code {"errors":[{"code":..., "description":...}]}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErroAsaas(List<Item> errors) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String code, String description) {
    }
}
