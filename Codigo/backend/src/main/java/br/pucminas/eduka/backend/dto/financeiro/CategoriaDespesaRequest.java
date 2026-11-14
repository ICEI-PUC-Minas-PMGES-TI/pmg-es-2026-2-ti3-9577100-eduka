package br.pucminas.eduka.backend.dto.financeiro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nova categoria de despesa da escola do token. */
public record CategoriaDespesaRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 80, message = "nome deve ter no máximo 80 caracteres") String nome) {
}
