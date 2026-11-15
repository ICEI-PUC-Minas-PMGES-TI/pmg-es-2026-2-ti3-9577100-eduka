package br.pucminas.eduka.backend.dto.acesso;

import jakarta.validation.constraints.NotBlank;

public record RenovarSessaoRequest(
        @NotBlank(message = "refreshToken é obrigatório") String refreshToken) {

    /** O refresh token e credencial: nunca vai para log. */
    @Override
    public String toString() {
        return "RenovarSessaoRequest[refreshToken=***]";
    }
}
