package br.pucminas.eduka.backend.dto.academico;

import java.time.LocalTime;
import java.util.List;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.HorarioTurma;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.academico.Turno;

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
            Integer cargaHoraria,
            /** Aulas da disciplina na grade da turma, por dia e hora (IU13); vazia sem horario montado. */
            List<HorarioDaDisciplina> horarios) {

        public static DisciplinaDoProfessor de(Disciplina disciplina, List<HorarioDaDisciplina> horarios) {
            return new DisciplinaDoProfessor(disciplina.getId(), disciplina.getNome(), disciplina.getCodigo(),
                    disciplina.getCargaHoraria(), horarios);
        }
    }

    /** Uma aula da disciplina na grade semanal da turma (HORARIO_TURMA). */
    public record HorarioDaDisciplina(DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFim) {

        public static HorarioDaDisciplina de(HorarioTurma horario) {
            return new HorarioDaDisciplina(horario.getDiaSemana(), horario.getHoraInicio(), horario.getHoraFim());
        }
    }

    public static TurmaDoProfessorResponse de(Turma turma, List<DisciplinaDoProfessor> disciplinas) {
        return new TurmaDoProfessorResponse(turma.getId(), turma.getNome(), turma.getCodigo(), turma.getTurno(),
                turma.getAtiva(), turma.getPeriodoLetivo().getId(), turma.getPeriodoLetivo().getNome(),
                turma.getCurso().getId(), turma.getCurso().getNome(), disciplinas);
    }
}
