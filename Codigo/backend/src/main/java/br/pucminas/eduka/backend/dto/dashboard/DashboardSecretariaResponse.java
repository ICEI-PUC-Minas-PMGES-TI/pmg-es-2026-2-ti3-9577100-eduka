package br.pucminas.eduka.backend.dto.dashboard;

import java.util.List;
import java.util.Map;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turno;

/**
 * Resumo de matriculas para a secretaria: como estao as matriculas, quantas
 * vagas sobram e onde.
 *
 * @param matriculasPorSituacao  as quatro situacoes, sempre presentes (zero
 *                               quando nao ha nenhuma)
 * @param matriculasUltimos30Dias matriculas com data nos ultimos 30 dias,
 *                               em qualquer situacao
 * @param alunosSemTurma         alunos cadastrados sem nenhuma matricula
 *                               ATIVA
 * @param turmas                 turmas ativas, da mais cheia para a mais
 *                               vazia
 */
public record DashboardSecretariaResponse(
        Map<StatusMatricula, Long> matriculasPorSituacao,
        long matriculasUltimos30Dias,
        long alunosSemTurma,
        Vagas vagas,
        List<OcupacaoTurma> turmas) {

    /**
     * Vagas das turmas ativas.
     *
     * @param turmasLotadas turmas sem vaga livre
     */
    public record Vagas(
            long ofertadas,
            long ocupadas,
            long livres,
            long turmasLotadas) {
    }

    public record OcupacaoTurma(
            Long turmaId,
            String turmaNome,
            String periodoLetivoNome,
            String cursoNome,
            Turno turno,
            int vagas,
            long matriculasAtivas) {
    }
}
