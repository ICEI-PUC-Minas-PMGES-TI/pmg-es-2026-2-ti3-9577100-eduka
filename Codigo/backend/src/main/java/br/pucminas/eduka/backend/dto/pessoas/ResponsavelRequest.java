package br.pucminas.eduka.backend.dto.pessoas;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Responsavel informado no cadastro do aluno ou incluido depois em
 * {@code POST /alunos/{alunoId}/responsaveis}. Se ja existe responsavel com o
 * mesmo CPF na escola (ex.: irmao ja matriculado), o aluno e ligado a ele e os
 * dados pessoais informados sao ignorados.
 *
 * @param cpf        opcional, menos para o financeiro, que e o pagador no
 *                   provedor; a conferencia fica em {@code ResponsavelService}
 * @param financeiro nulo equivale a {@code false}; no maximo um por aluno
 */
public record ResponsavelRequest(
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        String cpf,
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone,
        @Size(max = 30, message = "parentesco deve ter no máximo 30 caracteres") String parentesco,
        Boolean financeiro) {
}
