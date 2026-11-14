package br.pucminas.eduka.backend.dto.financeiro;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.financeiro.CategoriaDespesa;

public record CategoriaDespesaResponse(UUID id, String nome) {

    public static CategoriaDespesaResponse de(CategoriaDespesa categoria) {
        return new CategoriaDespesaResponse(categoria.getId(), categoria.getNome());
    }
}
