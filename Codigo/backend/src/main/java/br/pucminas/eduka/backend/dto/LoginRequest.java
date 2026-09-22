package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "login é obrigatório") String login,
        @NotBlank(message = "senha é obrigatória") String senha) {

    /** Impede que a senha apareca em log ou mensagem de erro. */
    @Override
    public String toString() {
        return "LoginRequest[login=%s]".formatted(login);
    }
}
