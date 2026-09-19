package br.pucminas.eduka.backend.dto;

import java.time.Instant;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.Escola;

public record EscolaResponse(
        UUID id,
        String nome,
        String cnpjCpf,
        String endereco,
        String email,
        String telefone,
        boolean ativo,
        Instant criadoEm) {

    public static EscolaResponse from(Escola escola) {
        return new EscolaResponse(
                escola.getId(),
                escola.getNome(),
                escola.getCnpjCpf(),
                escola.getEndereco(),
                escola.getEmail(),
                escola.getTelefone(),
                escola.isAtivo(),
                escola.getCriadoEm());
    }
}
