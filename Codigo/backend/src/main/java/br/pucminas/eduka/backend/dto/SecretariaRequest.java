package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de secretaria. Nao existe campo {@code escolaId} de proposito: a
 * escola vem sempre do token do MASTER autenticado, e o perfil e sempre
 * {@code SECRETARIA} (nunca vem do request).
 */
public record SecretariaRequest(
        @NotBlank(message = "nome é obrigatório") @Size(max = 150) String nome,
        @NotBlank(message = "cpf é obrigatório") @Pattern(regexp = "^\\d{11}$", message = "cpf deve ter 11 dígitos") String cpf,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String telefone,
        @NotBlank(message = "login é obrigatório") String login,
        @NotBlank(message = "senha é obrigatória") String senha) {

    @Override
    public String toString() { // nunca deixa a senha vazar em log/erro
        return "SecretariaRequest[nome=%s, login=%s]".formatted(nome, login);
    }
}
