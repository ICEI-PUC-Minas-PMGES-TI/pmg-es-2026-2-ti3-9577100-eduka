package br.pucminas.eduka.backend.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.domain.academico.TipoAvaliacao;

/**
 * Resumo do professor autenticado: suas turmas, seus alunos e o que vem pela
 * frente. So conta as turmas/disciplinas alocadas a ele hoje.
 *
 * @param alunos               alunos distintos com matricula ATIVA nas turmas
 *                             dele
 * @param proximasAvaliacoes   de hoje ate 30 dias, por data
 * @param avaliacoesPendentes  ja realizadas (data antes de hoje) com aluno ativo
 *                             ainda sem nota, da mais antiga para a mais
 *                             recente
 * @param aulasDaSemana        a grade (HORARIO_TURMA) das turmas ativas dele
 *                             nesta semana, de segunda a sabado, por dia e
 *                             hora; vazia sem horario montado
 */
public record DashboardProfessorResponse(
        long turmas,
        long disciplinas,
        long alunos,
        long avaliacoes,
        List<AvaliacaoResumo> proximasAvaliacoes,
        List<AvaliacaoResumo> avaliacoesPendentes,
        List<AulaDaSemana> aulasDaSemana) {

    /**
     * @param notasFaltando alunos ativos da turma ainda sem nota nesta
     *                      avaliacao
     */
    public record AvaliacaoResumo(
            UUID id,
            TipoAvaliacao tipo,
            String titulo,
            LocalDate data,
            BigDecimal valor,
            Long turmaId,
            String turmaNome,
            Long disciplinaId,
            String disciplinaNome,
            long notasFaltando) {
    }

    /**
     * Uma aula da grade do professor numa data desta semana.
     *
     * @param chamadaFeita ja ha chamada registrada para esta aula: no dia, a
     *                     n-esima aula da disciplina na turma conta como feita
     *                     quando ha ao menos n aulas registradas nele
     */
    public record AulaDaSemana(
            LocalDate data,
            DiaSemana diaSemana,
            LocalTime horaInicio,
            LocalTime horaFim,
            Long turmaId,
            String turmaNome,
            Long disciplinaId,
            String disciplinaNome,
            boolean chamadaFeita) {
    }
}
