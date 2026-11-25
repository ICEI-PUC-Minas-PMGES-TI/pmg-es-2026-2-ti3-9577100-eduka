package br.pucminas.eduka.backend.domain.academico;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Dias letivos da grade semanal. A ordem dos valores e a da semana, entao
 * {@code compareTo} ordena a grade.
 */
public enum DiaSemana {
    SEGUNDA,
    TERCA,
    QUARTA,
    QUINTA,
    SEXTA,
    SABADO;

    /** O dia da grade de uma data; domingo nao tem grade. */
    public static Optional<DiaSemana> de(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia == DayOfWeek.SUNDAY ? Optional.empty() : Optional.of(values()[dia.getValue() - 1]);
    }
}
