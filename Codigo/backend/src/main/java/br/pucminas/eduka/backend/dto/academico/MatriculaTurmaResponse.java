package br.pucminas.eduka.backend.dto.academico;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;

public record MatriculaTurmaResponse(
        UUID id,
        UUID alunoId,
        String alunoNome,
        /** Numero de matricula do aluno na escola (nao o id desta matricula). */
        String alunoNumeroMatricula,
        Long turmaId,
        String turmaNome,
        Turno turno,
        Long periodoLetivoId,
        String periodoLetivoNome,
        Long cursoId,
        String cursoNome,
        StatusMatricula status,
        LocalDate dataMatricula,
        String observacao,
        Instant criadoEm,
        Instant atualizadoEm) {

    /** {@code pessoa} e a do aluno, que guarda o nome (cifrado). */
    public static MatriculaTurmaResponse de(MatriculaTurma matricula, Pessoa pessoa) {
        Aluno aluno = matricula.getAluno();
        Turma turma = matricula.getTurma();
        return new MatriculaTurmaResponse(matricula.getId(), aluno.getId(), pessoa != null ? pessoa.getNome() : null,
                aluno.getMatricula(), turma.getId(), turma.getNome(), turma.getTurno(),
                turma.getPeriodoLetivo().getId(), turma.getPeriodoLetivo().getNome(), turma.getCurso().getId(),
                turma.getCurso().getNome(), matricula.getStatus(), matricula.getDataMatricula(),
                matricula.getObservacao(), matricula.getCriadoEm(), matricula.getAtualizadoEm());
    }
}
