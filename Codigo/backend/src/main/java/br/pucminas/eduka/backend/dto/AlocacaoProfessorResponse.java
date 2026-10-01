package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.domain.Turno;

public record AlocacaoProfessorResponse(
        UUID id,
        UUID professorId,
        String professorNome,
        Long turmaId,
        String turmaNome,
        Turno turno,
        Long periodoLetivoId,
        String periodoLetivoNome,
        Long disciplinaId,
        String disciplinaNome) {

    /** {@code pessoa} e a do professor, que guarda o nome (cifrado). */
    public static AlocacaoProfessorResponse de(AlocacaoProfessor alocacao, Pessoa pessoa) {
        Turma turma = alocacao.getTurma();
        Disciplina disciplina = alocacao.getDisciplina();
        return new AlocacaoProfessorResponse(alocacao.getId(), alocacao.getProfessor().getId(), pessoa.getNome(),
                turma.getId(), turma.getNome(), turma.getTurno(), turma.getPeriodoLetivo().getId(),
                turma.getPeriodoLetivo().getNome(), disciplina.getId(), disciplina.getNome());
    }
}
