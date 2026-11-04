package br.pucminas.eduka.backend.dto;

import java.time.LocalTime;

import br.pucminas.eduka.backend.domain.HorarioAula;

/**
 * Uma aula semanal da disciplina na turma (IU13).
 *
 * @param diaSemana ISO-8601: 1 = segunda ... 6 = sabado
 */
public record HorarioAulaResponse(Long disciplinaId, int diaSemana, LocalTime horaInicio) {

    public static HorarioAulaResponse de(HorarioAula horario) {
        return new HorarioAulaResponse(horario.getDisciplinaId(), horario.getDiaSemana(), horario.getHoraInicio());
    }
}
