package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import br.pucminas.eduka.backend.domain.StatusMatricula;

/**
 * Boletim do aluno numa turma: cada disciplina da grade com as avaliacoes, as
 * notas e os totais de pontos.
 */
public record BoletimTurmaResponse(
        Long turmaId,
        String turmaNome,
        String periodoLetivoNome,
        String cursoNome,
        StatusMatricula situacaoMatricula,
        List<BoletimDisciplina> disciplinas) {

    /**
     * @param pontosObtidos      soma das notas lancadas
     * @param pontosAvaliados    soma do valor das avaliacoes que ja tem nota
     *                           (base para o aproveitamento ate aqui)
     * @param pontosDistribuidos soma do valor de todas as avaliacoes da
     *                           disciplina, com ou sem nota
     */
    public record BoletimDisciplina(
            Long disciplinaId,
            String disciplinaNome,
            String professorNome,
            List<AvaliacaoDoAlunoResponse> avaliacoes,
            BigDecimal pontosObtidos,
            BigDecimal pontosAvaliados,
            BigDecimal pontosDistribuidos) {
    }
}
