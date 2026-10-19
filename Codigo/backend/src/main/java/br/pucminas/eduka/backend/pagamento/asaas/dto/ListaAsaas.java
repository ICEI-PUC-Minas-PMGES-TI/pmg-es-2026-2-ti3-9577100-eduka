package br.pucminas.eduka.backend.pagamento.asaas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Envelope das listagens do Asaas ({@code object: "list"}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListaAsaas<T>(List<T> data) {

    public List<T> itens() {
        return data == null ? List.of() : data;
    }
}
