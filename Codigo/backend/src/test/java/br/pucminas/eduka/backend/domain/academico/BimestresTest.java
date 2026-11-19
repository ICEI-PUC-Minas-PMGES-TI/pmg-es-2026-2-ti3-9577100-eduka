package br.pucminas.eduka.backend.domain.academico;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.domain.academico.Bimestres.Bimestre;

class BimestresTest {

    @Test
    void periodoAnualTemQuatroBimestresIguaisSemBuracos() {
        Bimestres ano = Bimestres.entre(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 12, 11));

        assertThat(ano.lista()).extracting(Bimestre::numero).containsExactly(1, 2, 3, 4);
        assertThat(ano.lista().getFirst().inicio()).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(ano.lista().getLast().fim()).isEqualTo(LocalDate.of(2026, 12, 11));
        for (int i = 1; i < 4; i++) {
            assertThat(ano.lista().get(i).inicio()).isEqualTo(ano.lista().get(i - 1).fim().plusDays(1));
        }
    }

    @Test
    void primeiroSemestreTemO1oEO2oBimestreESegundoO3oEO4o() {
        Bimestres primeiro = Bimestres.entre(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 7, 3));
        Bimestres segundo = Bimestres.entre(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 12, 18));

        assertThat(primeiro.lista()).extracting(Bimestre::numero).containsExactly(1, 2);
        assertThat(segundo.lista()).extracting(Bimestre::numero).containsExactly(3, 4);
        assertThat(primeiro.lista().get(1).fim()).isEqualTo(LocalDate.of(2026, 7, 3));
    }

    @Test
    void dataCaiNoBimestreDelaEForaDoPeriodoNoMaisProximo() {
        Bimestres primeiro = Bimestres.entre(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 7, 3));
        LocalDate fimDo1o = primeiro.lista().getFirst().fim();

        assertThat(primeiro.numeroDe(fimDo1o)).isEqualTo(1);
        assertThat(primeiro.numeroDe(fimDo1o.plusDays(1))).isEqualTo(2);
        assertThat(primeiro.numeroDe(LocalDate.of(2026, 1, 15))).isEqualTo(1);
        assertThat(primeiro.numeroDe(LocalDate.of(2026, 7, 20))).isEqualTo(2);
    }

    @Test
    void periodoSemDatasNaoTemBimestre() {
        Bimestres sem = Bimestres.doPeriodo(PeriodoLetivo.builder().nome("2026").build());

        assertThat(sem.lista()).isEmpty();
        assertThat(sem.numeroDe(LocalDate.of(2026, 3, 1))).isNull();
    }
}
