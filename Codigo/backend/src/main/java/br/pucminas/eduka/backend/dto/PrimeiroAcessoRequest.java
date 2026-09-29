package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PrimeiroAcessoRequest(
        @NotBlank(message = "login é obrigatório") String login,
        @NotBlank(message = "senha provisória é obrigatória") String senhaProvisoria,
        @NotBlank(message = "nova senha é obrigatória") String novaSenha) {

    /** Impede que as senhas aparecam em log ou mensagem de erro. */
    @Override
    public String toString() {
        return "PrimeiroAcessoRequest[login=%s]".formatted(login);
    }
}
