package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import br.pucminas.eduka.backend.domain.StatusMatricula;

/**
 * Boletim do aluno numa turma: cada disciplina da grade com as avaliacoes, as
 * notas, os totais de pontos e a frequencia (aulas, faltas e alerta).
 *
 * @param frequencia             da turma toda (soma das disciplinas da grade);
 *                               {@code null} se nenhuma teve aula com chamada
 * @param limiteFaltasPercentual limite de faltas, em % das aulas dadas, que
 *                               dispara o alerta; e o mesmo numero que o
 *                               texto "Regras de aprovacao" do boletim mostra
 */
public record BoletimTurmaResponse(
        Long turmaId,
        String turmaNome,
        String periodoLetivoNome,
        String cursoNome,
        StatusMatricula situacaoMatricula,
        List<BoletimDisciplina> disciplinas,
        Frequencia frequencia,
        int limiteFaltasPercentual) {

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
     */
    public record BoletimDisciplina(
            Long disciplinaId,
            String disciplinaNome,
            String professorNome,
            List<AvaliacaoDoAlunoResponse> avaliacoes,
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
