package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * Vinculo de um responsavel com um aluno. Marcar {@code financeiro} tira a
 * marca do responsavel financeiro anterior do aluno, se houver.
 *
 * @param financeiro nulo equivale a {@code false}
 */
public record VinculoResponsavelRequest(
        @Size(max = 30, message = "parentesco deve ter no máximo 30 caracteres") String parentesco,
        Boolean financeiro) {
}
