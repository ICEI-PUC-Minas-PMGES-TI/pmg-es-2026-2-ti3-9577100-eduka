package br.pucminas.eduka.backend.dto;

public record ResponsavelRequest(
        String nome,
        String cpf,
        String email,
        String telefone,
        String parentesco) {
}
