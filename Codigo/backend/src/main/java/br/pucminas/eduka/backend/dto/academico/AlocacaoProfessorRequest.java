package br.pucminas.eduka.backend.dto.academico;

import jakarta.validation.constraints.NotNull;

/**
 * Aloca o professor da URL em uma disciplina de uma turma. Sem
 * {@code escolaId}: a escola vem do token.
 */
public record AlocacaoProfessorRequest(
        @NotNull(message = "turmaId é obrigatório") Long turmaId,
        @NotNull(message = "disciplinaId é obrigatório") Long disciplinaId) {
}
