package br.pucminas.eduka.backend.dto;

/**
 * Dados de cadastro de aluno. Nao existe campo {@code escolaId} de proposito:
 * a escola vem sempre do token do usuario autenticado.
 */
public record AlunoRequest(
        String nome,
        String matricula,
        String emailResponsavel,
        String telefoneResponsavel,
        String genero) {
}
