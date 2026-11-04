package br.pucminas.eduka.backend.dto;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Horarios da disciplina na turma, substituindo os que havia. Lista vazia
 * apaga todos.
 */
public record DefinirHorariosRequest(
        @NotNull(message = "horarios é obrigatório") List<@Valid @NotNull Horario> horarios) {

    /** @param diaSemana ISO-8601: 1 = segunda ... 6 = sabado */
    public record Horario(
            @Min(value = 1, message = "diaSemana vai de 1 (segunda) a 6 (sábado)")
            @Max(value = 6, message = "diaSemana vai de 1 (segunda) a 6 (sábado)") int diaSemana,
            @NotNull(message = "horaInicio é obrigatória") LocalTime horaInicio) {
    }
}
