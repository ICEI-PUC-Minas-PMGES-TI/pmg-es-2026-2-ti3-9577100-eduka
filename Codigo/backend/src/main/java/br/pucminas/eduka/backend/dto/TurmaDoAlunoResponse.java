package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.Turno;

/**
 * Uma turma do aluno autenticado, pela matricula dele, com a grade de
 * disciplinas e quem leciona cada uma.
 */
public record TurmaDoAlunoResponse(
        UUID matriculaId,
        StatusMatricula situacaoMatricula,
        LocalDate dataMatricula,
        Long turmaId,
        String turmaNome,
        String turmaCodigo,
        Turno turno,
        String periodoLetivoNome,
        String cursoNome,
        List<DisciplinaDaTurma> disciplinas) {

    /** @param professorNome nulo enquanto a secretaria nao alocou professor */
    public record DisciplinaDaTurma(
            Long id,
            String nome,
            String codigo,
            Integer cargaHoraria,
            String professorNome) {
    }
}
