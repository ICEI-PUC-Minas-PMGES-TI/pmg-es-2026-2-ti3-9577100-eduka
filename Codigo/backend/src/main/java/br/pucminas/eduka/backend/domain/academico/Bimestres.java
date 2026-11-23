package br.pucminas.eduka.backend.domain.academico;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Os bimestres de um periodo letivo, calculados pelas datas dele (sem
 * cadastro a parte). O ano letivo tem quatro bimestres:
 *
 * <ul>
 * <li>Periodo anual (mais de {@value #DIAS_MAXIMOS_DO_SEMESTRE} dias): dividido
 * em quatro partes iguais, do 1o ao 4o bimestre.</li>
 * <li>Periodo semestral: dividido em duas partes iguais. O semestre que comeca
 * no primeiro semestre do ano tem o 1o e o 2o bimestre; o que comeca de julho
 * em diante, o 3o e o 4o.</li>
 * </ul>
 *
 * <p>
 * Uma data fora do periodo cai no bimestre mais proximo (antes do inicio, o
 * primeiro; depois do fim, o ultimo): avaliacao lancada fora das datas ainda
 * aparece no boletim.
 * </p>
 */
public final class Bimestres {

    /** Ate aqui (cerca de 7 meses), o periodo e um semestre: dois bimestres. */
    static final int DIAS_MAXIMOS_DO_SEMESTRE = 220;

    /**
     * Um bimestre, com as datas que ele cobre (inclusivas).
     *
     * @param numero de 1 a 4, no ano letivo
     */
    public record Bimestre(int numero, LocalDate inicio, LocalDate fim) {
    }

    private final List<Bimestre> lista;

    private Bimestres(List<Bimestre> lista) {
        this.lista = List.copyOf(lista);
    }

    /** Os bimestres do periodo; vazio se o periodo nao tem as duas datas. */
    public static Bimestres doPeriodo(PeriodoLetivo periodo) {
        return periodo == null ? new Bimestres(List.of()) : entre(periodo.getDataInicio(), periodo.getDataFim());
    }

    /** Os bimestres entre {@code inicio} e {@code fim} (inclusivos); vazio sem datas. */
    public static Bimestres entre(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            return new Bimestres(List.of());
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        boolean semestre = dias <= DIAS_MAXIMOS_DO_SEMESTRE;
        int partes = semestre ? 2 : 4;
        int primeiro = semestre && inicio.getMonthValue() >= Month.JULY.getValue() ? 3 : 1;
        List<Bimestre> lista = new ArrayList<>(partes);
        for (int i = 0; i < partes; i++) {
            LocalDate de = inicio.plusDays(dias * i / partes);
            LocalDate ate = inicio.plusDays(dias * (i + 1) / partes - 1);
            lista.add(new Bimestre(primeiro + i, de, ate));
        }
        return new Bimestres(lista);
    }

    public List<Bimestre> lista() {
        return lista;
    }

    /**
     * Numero do bimestre da data; {@code null} se o periodo nao tem datas.
     * Fora do periodo, o bimestre mais proximo.
     */
    public Integer numeroDe(LocalDate data) {
        if (lista.isEmpty() || data == null) {
            return null;
        }
        if (data.isBefore(lista.getFirst().inicio())) {
            return lista.getFirst().numero();
        }
        return lista.stream().filter(b -> !data.isAfter(b.fim())).findFirst().orElse(lista.getLast()).numero();
    }
}
