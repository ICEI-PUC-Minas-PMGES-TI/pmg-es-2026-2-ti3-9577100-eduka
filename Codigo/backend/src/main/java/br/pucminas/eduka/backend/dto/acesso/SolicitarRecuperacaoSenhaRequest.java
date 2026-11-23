package br.pucminas.eduka.backend.dto.acesso;

import jakarta.validation.constraints.NotBlank;

public record SolicitarRecuperacaoSenhaRequest(
        @NotBlank(message = "login é obrigatório") String login) {
}
