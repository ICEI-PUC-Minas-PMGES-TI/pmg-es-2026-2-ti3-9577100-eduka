package br.pucminas.eduka.backend.dto.academico;

import java.time.LocalTime;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import jakarta.validation.constraints.NotNull;

/**
 * Cria uma aula na grade da turma da URL. A alocacao da a disciplina e o
 * professor e precisa ser da turma. Sem {@code escolaId}: a escola vem do
 * token.
 */
public record HorarioTurmaRequest(
        @NotNull(message = "alocacaoId é obrigatório") UUID alocacaoId,
        @NotNull(message = "diaSemana é obrigatório") DiaSemana diaSemana,
        @NotNull(message = "horaInicio é obrigatório") LocalTime horaInicio,
        @NotNull(message = "horaFim é obrigatório") LocalTime horaFim) {
}
