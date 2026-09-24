package br.pucminas.eduka.backend.dto;

import java.util.UUID;

public record ResponsavelResponse(
        UUID pessoaId,
        String nome,
        String email,
        String telefone,
        String parentesco) {
}
