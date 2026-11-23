package br.pucminas.eduka.backend.mock;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.domain.academico.Turno;

/**
 * Grade semanal da escola de demonstracao: espalha as aulas de cada
 * disciplina pelos dias da semana sem por a turma, nem o professor, em duas
 * aulas ao mesmo tempo (a mesma regra do HorarioTurmaService).
 *
 * <p>
 * Cinco aulas de 50 minutos por turno, com intervalo depois da terceira, de
 * segunda a sexta. Deterministico: a mesma entrada da sempre a mesma grade.
 * </p>
 */
final class GradeMock {

    /** Inicio das cinco aulas de cada turno; o intervalo fica entre a terceira e a quarta. */
    private static final Map<Turno, List<LocalTime>> INICIOS = Map.of(
            Turno.MATUTINO, horas("07:00", "07:50", "08:40", "09:50", "10:40"),
            Turno.VESPERTINO, horas("13:00", "13:50", "14:40", "15:50", "16:40"),
            Turno.NOTURNO, horas("18:30", "19:20", "20:10", "21:10", "22:00"),
            Turno.INTEGRAL, horas("07:00", "07:50", "08:40", "09:50", "10:40"));
    private static final int DURACAO_MINUTOS = 50;
    private static final List<DiaSemana> DIAS = List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA,
            DiaSemana.QUINTA, DiaSemana.SEXTA);

    /**
     * Uma disciplina de uma turma, com quantas aulas ela tem por semana.
     *
     * @param alocacao  o que a aula montada devolve (a alocacao, no seeder)
     * @param turma     identifica a turma: duas aulas dela nao se sobrepoem
     * @param professor identifica o professor: idem, em qualquer turma
     */
    record Pedido<T>(T alocacao, Object turma, Object professor, Turno turno, int aulasPorSemana) {
    }

    record Aula<T>(T alocacao, DiaSemana dia, LocalTime inicio, LocalTime fim) {
    }

    private GradeMock() {
    }

    /**
     * Encaixa as aulas pedidas, as disciplinas com mais aulas primeiro. Cada
     * disciplina comeca num dia diferente e pula um dia entre as aulas, sem
     * repetir dia enquanto da; a aula que nao cabe em lugar nenhum fica de fora.
     */
    static <T> List<Aula<T>> montar(List<Pedido<T>> pedidos) {
        Set<String> ocupados = new HashSet<>();
        Map<Object, Integer> turmas = new LinkedHashMap<>();
        pedidos.forEach(p -> turmas.putIfAbsent(p.turma(), turmas.size()));

        List<Pedido<T>> ordem = new ArrayList<>(pedidos);
        ordem.sort(Comparator.comparingInt((Pedido<T> p) -> p.aulasPorSemana()).reversed());

        List<Aula<T>> aulas = new ArrayList<>();
        for (int indice = 0; indice < ordem.size(); indice++) {
            Pedido<T> pedido = ordem.get(indice);
            int inicio = turmas.get(pedido.turma()) + indice;
            Set<DiaSemana> diasUsados = new HashSet<>();
            for (int aula = 0; aula < pedido.aulasPorSemana(); aula++) {
                int preferido = inicio + aula * 2;
                Aula<T> encaixada = encaixar(pedido, preferido, diasUsados, ocupados, false);
                if (encaixada == null) {
                    encaixada = encaixar(pedido, preferido, diasUsados, ocupados, true);
                }
                if (encaixada != null) {
                    diasUsados.add(encaixada.dia());
                    aulas.add(encaixada);
                }
            }
        }
        aulas.sort(Comparator.comparing((Aula<T> a) -> a.dia()).thenComparing(Aula::inicio));
        return aulas;
    }

    private static <T> Aula<T> encaixar(Pedido<T> pedido, int diaPreferido, Set<DiaSemana> diasUsados,
            Set<String> ocupados, boolean repetirDia) {
        List<LocalTime> inicios = INICIOS.get(pedido.turno());
        for (int d = 0; d < DIAS.size(); d++) {
            DiaSemana dia = DIAS.get(Math.floorMod(diaPreferido + d, DIAS.size()));
            if (!repetirDia && diasUsados.contains(dia)) {
                continue;
            }
            for (LocalTime hora : inicios) {
                String turma = "T|" + pedido.turma() + "|" + dia + "|" + hora;
                String professor = "P|" + pedido.professor() + "|" + dia + "|" + hora;
                if (!ocupados.contains(turma) && !ocupados.contains(professor)) {
                    ocupados.add(turma);
                    ocupados.add(professor);
                    return new Aula<>(pedido.alocacao(), dia, hora, hora.plusMinutes(DURACAO_MINUTOS));
                }
            }
        }
        return null;
    }

    private static List<LocalTime> horas(String... inicios) {
        return Arrays.stream(inicios).map(LocalTime::parse).toList();
    }
}
