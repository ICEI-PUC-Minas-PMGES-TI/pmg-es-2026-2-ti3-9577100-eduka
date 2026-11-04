package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.StatusMatricula;

/** Aluno matriculado na turma, para o Ver alunos do professor (IU13). */
public record AlunoDaTurmaResponse(UUID alunoId, String nome, String numeroMatricula, StatusMatricula situacao) {
}
