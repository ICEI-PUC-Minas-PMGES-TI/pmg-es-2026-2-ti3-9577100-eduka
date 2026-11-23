package br.pucminas.eduka.backend.dto.acesso;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ReenviarCodigoRequest(
        @NotNull(message = "verificação é obrigatória") UUID verificacaoId) {
}
