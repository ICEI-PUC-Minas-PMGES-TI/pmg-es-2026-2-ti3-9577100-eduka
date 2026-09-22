package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.Aluno;

public record AlunoResponse(
        UUID id,
        UUID escolaId,
        String nome,
        String matricula,
        String emailResponsavel,
        String telefoneResponsavel,
        String genero) {

    public static AlunoResponse de(Aluno aluno) {
        return new AlunoResponse(
                aluno.getId(),
                aluno.getEscolaId(),
                aluno.getNome(),
                aluno.getMatricula(),
                aluno.getEmailResponsavel(),
                aluno.getTelefoneResponsavel(),
                aluno.getGenero());
    }
}
