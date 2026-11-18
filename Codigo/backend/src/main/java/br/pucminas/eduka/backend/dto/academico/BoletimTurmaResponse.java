package br.pucminas.eduka.backend.dto.academico;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;

/**
 * Boletim do aluno numa turma: cada disciplina da grade com as avaliacoes, as
 * notas, os totais de pontos e a frequencia (aulas, faltas e alerta).
 *
 * @param frequencia             da turma toda (soma das disciplinas da grade);
 *                               {@code null} se nenhuma teve aula com chamada
 * @param limiteFaltasPercentual limite de faltas, em % das aulas dadas, que
 *                               dispara o alerta; e o mesmo numero que o
 *                               texto "Regras de aprovacao" do boletim mostra
 * @param ano                    ano letivo (do inicio do periodo); nulo se o
 *                               periodo nao tem datas
 * @param bimestres              os bimestres do periodo (ver
 *                               {@link br.pucminas.eduka.backend.domain.academico.Bimestres}),
 *                               com a frequencia da turma em cada um; vazio se
 *                               o periodo nao tem datas
 */
public record BoletimTurmaResponse(
        Long turmaId,
        String turmaNome,
        String periodoLetivoNome,
        String cursoNome,
        StatusMatricula situacaoMatricula,
        List<BoletimDisciplina> disciplinas,
        Frequencia frequencia,
        int limiteFaltasPercentual,
        Integer ano,
        LocalDate periodoLetivoInicio,
        LocalDate periodoLetivoFim,
        List<BimestreDaTurma> bimestres) {

    /** Limite unico de faltas (% das aulas dadas): acima dele, alerta. */
    public static final int LIMITE_FALTAS_PERCENTUAL = 25;

    /**
     * @param pontosObtidos      soma das notas lancadas
     * @param pontosAvaliados    soma do valor das avaliacoes que ja tem nota
     *                           (base para o aproveitamento ate aqui)
     * @param pontosDistribuidos soma do valor de todas as avaliacoes da
     *                           disciplina, com ou sem nota
     * @param frequencia         {@code null} enquanto a disciplina nao teve
     *                           aula com chamada ("sem aulas")
     * @param bimestres          os mesmos totais e a frequencia em cada
     *                           bimestre da turma, na ordem
     */
    public record BoletimDisciplina(
            Long disciplinaId,
            String disciplinaNome,
            String professorNome,
            List<AvaliacaoDoAlunoResponse> avaliacoes,
            BigDecimal pontosObtidos,
            BigDecimal pontosAvaliados,
            BigDecimal pontosDistribuidos,
            Frequencia frequencia,
            List<BimestreDaDisciplina> bimestres) {
    }

    /**
     * Um bimestre da turma: as datas que ele cobre e a frequencia do aluno
     * nele (todas as disciplinas); {@code null} sem aula com chamada.
     */
    public record BimestreDaTurma(
            int numero,
            LocalDate inicio,
            LocalDate fim,
            Frequencia frequencia) {
    }

    /** A disciplina num bimestre: os totais de pontos e a frequencia so dele. */
    public record BimestreDaDisciplina(
            int numero,
            BigDecimal pontosObtidos,
            BigDecimal pontosAvaliados,
            BigDecimal pontosDistribuidos,
            Frequencia frequencia) {
    }

    /**
     * Frequencia do aluno. Nunca existe com zero aulas: sem aula registrada o
     * campo {@code frequencia} do boletim vai {@code null}, e a tela mostra
     * "sem aulas" em vez de 0% ou 100%.
     *
     * @param totalAulas           aulas com chamada em que o aluno consta
     * @param faltas               aulas em que o aluno estava ausente
     * @param percentualFrequencia presenca em %, com uma casa decimal
     *                             (arredondamento meio para cima)
     * @param alertaFaltas         faltas estritamente acima de
     *                             {@link BoletimTurmaResponse#LIMITE_FALTAS_PERCENTUAL}% das
     *                             aulas dadas (25% exatos nao alertam)
     */
    public record Frequencia(
            int totalAulas,
            int faltas,
            BigDecimal percentualFrequencia,
            boolean alertaFaltas) {

        /** {@code null} quando nao houve aula (nao ha frequencia a calcular). */
        public static Frequencia de(long totalAulas, long faltas) {
            if (totalAulas <= 0) {
                return null;
            }
            BigDecimal presenca = BigDecimal.valueOf((totalAulas - faltas) * 100L)
                    .divide(BigDecimal.valueOf(totalAulas), 1, RoundingMode.HALF_UP);
            // Comparacao em inteiros (faltas/aulas > limite/100), sem arredondar antes.
            boolean alerta = faltas * 100 > totalAulas * LIMITE_FALTAS_PERCENTUAL;
            return new Frequencia(Math.toIntExact(totalAulas), Math.toIntExact(faltas), presenca, alerta);
        }

        /** Soma as frequencias que existem; {@code null} se nenhuma tem aula. */
        public static Frequencia somar(List<Frequencia> frequencias) {
            long aulas = 0;
            long faltas = 0;
            for (Frequencia f : frequencias) {
                if (f != null) {
                    aulas += f.totalAulas();
                    faltas += f.faltas();
                }
            }
            return de(aulas, faltas);
        }
    }
}
