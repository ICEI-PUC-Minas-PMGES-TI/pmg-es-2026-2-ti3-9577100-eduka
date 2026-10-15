package br.pucminas.eduka.backend.dto;

/**
 * Responsavel informado junto com o cadastro do aluno. Se ja existe
 * responsavel com o mesmo CPF na escola (ex.: irmao ja matriculado), o
 * aluno e ligado a ele e os demais campos sao ignorados.
 *
 * @param financeiro nulo equivale a {@code false}; no maximo um por aluno
 */
public record ResponsavelRequest(
        String nome,
        String cpf,
        String email,
        String telefone,
        String parentesco,
        Boolean financeiro) {
}
