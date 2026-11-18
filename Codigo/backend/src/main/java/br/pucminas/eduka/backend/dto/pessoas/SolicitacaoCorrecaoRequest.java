package br.pucminas.eduka.backend.dto.pessoas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O que o aluno quer corrigir, em texto livre. */
public record SolicitacaoCorrecaoRequest(
        @NotBlank(message = "mensagem é obrigatória")
        @Size(max = 500, message = "mensagem deve ter no máximo 500 caracteres") String mensagem) {
}
