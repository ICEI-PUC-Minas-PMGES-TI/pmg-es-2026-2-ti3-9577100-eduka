package br.pucminas.eduka.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Periodo escolhido nas consultas financeiras: de {@code de} a {@code ate},
 * inclusivos, no fuso da escola.
 *
 * @throws IllegalArgumentException se o periodo esta invertido ou passa de
 *                                  {@link #MAXIMO_DE_MESES} meses
 */
record PeriodoFinanceiro(LocalDate de, LocalDate ate) {

    static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    /** Mais que isso, as barras por mes nao cabem na tela. */
    static final int MAXIMO_DE_MESES = 24;

    PeriodoFinanceiro {
        if (de.isAfter(ate)) {
            throw new IllegalArgumentException("de nao pode ser depois de ate");
        }
        if (YearMonth.from(de).plusMonths(MAXIMO_DE_MESES).isBefore(YearMonth.from(ate).plusMonths(1))) {
            throw new IllegalArgumentException("O periodo pode ter no maximo " + MAXIMO_DE_MESES + " meses");
        }
    }

    /** Meia-noite do primeiro dia. */
    Instant inicio() {
        return de.atStartOfDay(FUSO_ESCOLA).toInstant();
    }

    /** Meia-noite do dia seguinte ao ultimo: o limite exclusivo. */
    Instant fim() {
        return ate.plusDays(1).atStartOfDay(FUSO_ESCOLA).toInstant();
    }

    YearMonth primeiroMes() {
        return YearMonth.from(de);
    }

    YearMonth ultimoMes() {
        return YearMonth.from(ate);
    }
}
