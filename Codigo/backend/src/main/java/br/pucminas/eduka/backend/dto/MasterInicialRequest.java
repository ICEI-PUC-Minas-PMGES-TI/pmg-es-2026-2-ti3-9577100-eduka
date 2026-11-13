package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.validation.Cpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Quem vai administrar a escola: o primeiro login MASTER, criado junto com a
 * escola pelo SUPER_ADMIN.
 *
 * <p>
 * Como na equipe, nao ha login nem senha: o e-mail vira o login e o primeiro
 * acesso vai para ele, com o CPF (so digitos) de senha provisoria (ver
 * {@code AcessoService}).
 * </p>
 */
public record MasterInicialRequest(
        @NotBlank(message = "nome do gestor é obrigatório")
        @Size(max = 150, message = "nome do gestor deve ter no máximo 150 caracteres") String nome,
        @NotBlank(message = "cpf do gestor é obrigatório")
        @Cpf String cpf,
        @NotBlank(message = "email do gestor é obrigatório")
        @Email(message = "email do gestor deve ser um endereço válido")
        @Size(max = 150, message = "email do gestor deve ter no máximo 150 caracteres") String email) {
}
