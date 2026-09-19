package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record EscolaUpdateRequest(
        @NotBlank(message = "nome é obrigatório") String nome,
        String cnpjCpf,
        String endereco,
        String email,
        String telefone) {
}
