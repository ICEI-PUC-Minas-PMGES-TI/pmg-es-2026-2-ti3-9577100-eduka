package br.pucminas.eduka.backend.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.domain.academico.Turno;

/** A grade da escola de demonstracao: tudo encaixado, sem choque de turma nem de professor. */
class GradeMockTest {

    /** Disciplina -> professor e aulas por semana, como no MockDataSeeder. */
    private static final Map<String, Object[]> DISCIPLINAS = Map.of(
            "MAT", new Object[] { "Ana", 4 }, "POR", new Object[] { "Carlos", 4 },
            "CIE", new Object[] { "Juliana", 3 }, "BIO", new Object[] { "Juliana", 2 },
            "FIS", new Object[] { "Fernando", 2 }, "QUI", new Object[] { "Fernando", 2 },
            "HIS", new Object[] { "Luciana", 2 }, "GEO", new Object[] { "Ricardo", 2 },
            "ING", new Object[] { "Beatriz", 2 }, "EDF", new Object[] { "Thiago", 2 });
    private static final List<String> FUNDAMENTAL = List.of("MAT", "POR", "CIE", "HIS", "GEO", "ING", "EDF");
    private static final List<String> MEDIO = List.of("MAT", "POR", "BIO", "FIS", "QUI", "HIS", "GEO", "ING", "EDF");

    private record Alocacao(String turma, String disciplina, String professor) {
    }

    private static List<GradeMock.Pedido<Alocacao>> pedidosDaEscola() {
        Object[][] turmas = {
                { "6A", FUNDAMENTAL, Turno.MATUTINO }, { "8A", FUNDAMENTAL, Turno.MATUTINO },
                { "9B", FUNDAMENTAL, Turno.VESPERTINO }, { "1A", MEDIO, Turno.MATUTINO },
                { "3A", MEDIO, Turno.MATUTINO } };
        List<GradeMock.Pedido<Alocacao>> pedidos = new ArrayList<>();
        for (Object[] turma : turmas) {
            @SuppressWarnings("unchecked")
            List<String> grade = (List<String>) turma[1];
            for (String disciplina : grade) {
                String professor = (String) DISCIPLINAS.get(disciplina)[0];
                int aulas = (Integer) DISCIPLINAS.get(disciplina)[1];
                pedidos.add(new GradeMock.Pedido<>(new Alocacao((String) turma[0], disciplina, professor),
                        turma[0], professor, (Turno) turma[2], aulas));
            }
        }
        return pedidos;
    }

    @Test
    void encaixaTodasAsAulasSemChoqueDeTurmaNemDeProfessor() {
        List<GradeMock.Pedido<Alocacao>> pedidos = pedidosDaEscola();

        List<GradeMock.Aula<Alocacao>> grade = GradeMock.montar(pedidos);

        int pedidas = pedidos.stream().mapToInt(GradeMock.Pedido::aulasPorSemana).sum();
        assertThat(grade).hasSize(pedidas);
        Function<GradeMock.Aula<Alocacao>, String> horaDaTurma = a -> a.alocacao().turma() + a.dia() + a.inicio();
        Function<GradeMock.Aula<Alocacao>, String> horaDoProfessor = a -> a.alocacao().professor() + a.dia()
                + a.inicio();
        assertThat(grade.stream().map(horaDaTurma).distinct()).hasSize(grade.size());
        assertThat(grade.stream().map(horaDoProfessor).distinct()).hasSize(grade.size());
    }

    @Test
    void espalhaAsAulasDeUmaDisciplinaPorDiasDiferentes() {
        List<GradeMock.Aula<Alocacao>> grade = GradeMock.montar(pedidosDaEscola());

        Map<Alocacao, Long> diasPorDisciplina = grade.stream().collect(Collectors.groupingBy(
                GradeMock.Aula::alocacao, Collectors.mapping(GradeMock.Aula::dia,
                        Collectors.collectingAndThen(Collectors.toSet(), s -> (long) s.size()))));
        Map<Alocacao, Long> aulasPorDisciplina = grade.stream()
                .collect(Collectors.groupingBy(GradeMock.Aula::alocacao, Collectors.counting()));

        assertThat(diasPorDisciplina).isEqualTo(aulasPorDisciplina);
    }

    @Test
    void mesmaEntradaDaMesmaGrade() {
        assertThat(GradeMock.montar(pedidosDaEscola())).isEqualTo(GradeMock.montar(pedidosDaEscola()));
    }
}
