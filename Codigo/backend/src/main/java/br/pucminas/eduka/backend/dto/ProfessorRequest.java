package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro e edicao de professor. Na edicao substitui o registro inteiro:
 * campo opcional enviado nulo apaga o valor anterior.
 *
 * <p>
 * Nao existe campo {@code escolaId} de proposito: a escola vem sempre do
 * token do usuario autenticado.
 * </p>
 */
public record ProfessorRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @NotBlank(message = "cpf é obrigatório")
        @Pattern(regexp = "^\\d{11}$", message = "cpf deve ter 11 dígitos") String cpf,
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone,
        @Size(max = 150, message = "formacao deve ter no máximo 150 caracteres") String formacao,
        LocalDate dataAdmissao) {
}
