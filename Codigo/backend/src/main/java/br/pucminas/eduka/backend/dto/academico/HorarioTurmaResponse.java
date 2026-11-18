package br.pucminas.eduka.backend.dto.academico;

import java.time.LocalTime;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.domain.academico.HorarioTurma;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;

public record HorarioTurmaResponse(
        UUID id,
        UUID alocacaoId,
        Long turmaId,
        DiaSemana diaSemana,
        LocalTime horaInicio,
        LocalTime horaFim,
        Long disciplinaId,
        String disciplinaNome,
        UUID professorId,
        String professorNome) {

    /** {@code pessoa} e a do professor, que guarda o nome (cifrado). */
    public static HorarioTurmaResponse de(HorarioTurma horario, Pessoa pessoa) {
        var alocacao = horario.getAlocacao();
        return new HorarioTurmaResponse(horario.getId(), alocacao.getId(), alocacao.getTurma().getId(),
                horario.getDiaSemana(), horario.getHoraInicio(), horario.getHoraFim(),
                alocacao.getDisciplina().getId(), alocacao.getDisciplina().getNome(),
                alocacao.getProfessor().getId(), pessoa.getNome());
    }
}
