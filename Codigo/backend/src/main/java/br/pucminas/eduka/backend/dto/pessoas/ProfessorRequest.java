package br.pucminas.eduka.backend.dto.pessoas;

import java.time.LocalDate;

import br.pucminas.eduka.backend.domain.pessoas.VinculoProfessor;
import br.pucminas.eduka.backend.validation.Cpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro e edicao de professor. Na edicao substitui o registro inteiro:
 * campo opcional enviado nulo apaga o valor anterior.
 *
 * <p>
 * Nao existe campo {@code escolaId} de proposito: a escola vem sempre do
 * token do usuario autenticado.
 * </p>
 *
 * <p>
 * E-mail e obrigatorio porque vira o login do professor e e para onde vai o
 * primeiro acesso.
 * </p>
 */
public record ProfessorRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @NotBlank(message = "cpf é obrigatório")
        @Cpf String cpf,
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone,
        @Size(max = 150, message = "formacao deve ter no máximo 150 caracteres") String formacao,
        LocalDate dataAdmissao,
        VinculoProfessor vinculo) {
}
