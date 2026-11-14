package br.pucminas.eduka.backend.dto.acesso;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerificarCodigoRequest(
        @NotNull(message = "verificação é obrigatória") UUID verificacaoId,
        @NotNull(message = "código é obrigatório")
        @Pattern(regexp = "\\d{6}", message = "código deve ter 6 números") String codigo) {

    /** Impede que o codigo apareca em log ou mensagem de erro. */
    @Override
    public String toString() {
        return "VerificarCodigoRequest[verificacaoId=%s]".formatted(verificacaoId);
    }
}
