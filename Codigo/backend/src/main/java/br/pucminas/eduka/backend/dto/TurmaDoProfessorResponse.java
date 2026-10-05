package br.pucminas.eduka.backend.dto;

import java.util.List;

import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.domain.Turno;

/**
 * Uma turma do professor autenticado, com apenas as disciplinas que ele
 * leciona nela - nao a grade inteira da turma.
 */
public record TurmaDoProfessorResponse(
        Long id,
        String nome,
        String codigo,
        Turno turno,
        Boolean ativa,
        Long periodoLetivoId,
        String periodoLetivoNome,
        Long cursoId,
        String cursoNome,
        List<DisciplinaDoProfessor> disciplinas) {

    public record DisciplinaDoProfessor(
            Long id,
            String nome,
            String codigo,
            Integer cargaHoraria) {

        public static DisciplinaDoProfessor de(Disciplina disciplina) {
            return new DisciplinaDoProfessor(disciplina.getId(), disciplina.getNome(), disciplina.getCodigo(),
                    disciplina.getCargaHoraria());
        }
    }

    public static TurmaDoProfessorResponse de(Turma turma, List<DisciplinaDoProfessor> disciplinas) {
        return new TurmaDoProfessorResponse(turma.getId(), turma.getNome(), turma.getCodigo(), turma.getTurno(),
                turma.getAtiva(), turma.getPeriodoLetivo().getId(), turma.getPeriodoLetivo().getNome(),
                turma.getCurso().getId(), turma.getCurso().getNome(), disciplinas);
    }
}
