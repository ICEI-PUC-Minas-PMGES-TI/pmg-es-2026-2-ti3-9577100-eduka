package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.TipoAvaliacao;

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
 */
public record DashboardProfessorResponse(
        long turmas,
        long disciplinas,
        long alunos,
        long avaliacoes,
        List<AvaliacaoResumo> proximasAvaliacoes,
        List<AvaliacaoResumo> avaliacoesPendentes) {

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
}
