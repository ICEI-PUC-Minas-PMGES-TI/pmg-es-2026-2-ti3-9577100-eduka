package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.validation.Cpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de alguem da equipe da escola: SECRETARIA ou FINANCEIRO.
 *
 * <p>
 * Nao ha login nem senha: o e-mail vira o login e o primeiro acesso vai para
 * ele, com o CPF como senha provisoria (ver {@code AcessoService}). Nao existe
 * campo {@code escolaId} de proposito: a escola vem sempre do token.
 * </p>
 */
public record MembroEquipeRequest(
        @NotNull(message = "perfil é obrigatório") PerfilUsuario perfil,
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @NotBlank(message = "cpf é obrigatório")
        @Cpf String cpf,
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone) {
}
