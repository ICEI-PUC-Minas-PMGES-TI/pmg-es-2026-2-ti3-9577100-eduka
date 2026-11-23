package br.pucminas.eduka.backend.dto.academico;

import java.time.LocalTime;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import jakarta.validation.constraints.NotNull;

/** Muda o dia e o horario de uma aula. A alocacao (disciplina e professor) nao muda. */
public record AtualizarHorarioTurmaRequest(
        @NotNull(message = "diaSemana é obrigatório") DiaSemana diaSemana,
        @NotNull(message = "horaInicio é obrigatório") LocalTime horaInicio,
        @NotNull(message = "horaFim é obrigatório") LocalTime horaFim) {
}
