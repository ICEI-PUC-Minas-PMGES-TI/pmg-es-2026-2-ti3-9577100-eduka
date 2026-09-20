package br.pucminas.eduka.backend.dto;

import java.time.Instant;
import java.util.UUID;

import br.pucminas.eduka.backend.domains.enums.Status;
import br.pucminas.eduka.backend.domains.model.Escola;

public record EscolaResponse(
        UUID id,
        String nome,
        String cnpj,
        String endereco,
        String email,
        String telefone,
        Status status,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static EscolaResponse de(Escola escola) {
        return new EscolaResponse(
                escola.getId(),
                escola.getNome(),
                escola.getCnpj(),
                escola.getEndereco(),
                escola.getEmail(),
                escola.getTelefone(),
                escola.getStatus(),
                escola.getCriadoEm(),
                escola.getAtualizadoEm());
    }
}
