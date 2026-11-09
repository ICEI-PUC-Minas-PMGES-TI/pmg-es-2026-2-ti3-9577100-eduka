package br.pucminas.eduka.backend.dto;

import java.util.UUID;

/** Um aluno que entra na chamada de um dia, ainda sem presenca marcada. */
public record AlunoDaChamadaResponse(
        UUID alunoId,
        String alunoNome,
        String alunoNumeroMatricula) {
}
