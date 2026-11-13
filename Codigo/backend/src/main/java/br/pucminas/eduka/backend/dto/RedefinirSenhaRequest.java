package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RedefinirSenhaRequest(
        @NotBlank(message = "login é obrigatório") String login,
        @NotNull(message = "código é obrigatório")
        @Pattern(regexp = "\\d{6}", message = "código deve ter 6 números") String codigo,
        @NotBlank(message = "nova senha é obrigatória") String novaSenha) {

    /** Impede que o codigo e a senha aparecam em log ou mensagem de erro. */
    @Override
    public String toString() {
        return "RedefinirSenhaRequest[login=%s]".formatted(login);
    }
}
