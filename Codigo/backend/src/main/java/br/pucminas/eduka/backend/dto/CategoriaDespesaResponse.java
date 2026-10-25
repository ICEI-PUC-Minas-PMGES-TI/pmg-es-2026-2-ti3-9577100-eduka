package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.CategoriaDespesa;

public record CategoriaDespesaResponse(UUID id, String nome) {

    public static CategoriaDespesaResponse de(CategoriaDespesa categoria) {
        return new CategoriaDespesaResponse(categoria.getId(), categoria.getNome());
    }
}
