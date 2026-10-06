package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Matricula um aluno numa turma. Sem {@code escolaId}: a escola vem do token,
 * e aluno e turma de outra escola respondem 404.
 *
 * @param dataMatricula opcional; sem ela, vale a data de hoje
 */
public record MatriculaTurmaRequest(
        @NotNull(message = "alunoId é obrigatório") UUID alunoId,
        @NotNull(message = "turmaId é obrigatório") Long turmaId,
        LocalDate dataMatricula,
        @Size(max = 500, message = "observacao deve ter no máximo 500 caracteres") String observacao) {
}
