package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Aceite do aviso de privacidade. A versao vem da tela para provar que o
 * usuario leu a versao em vigor, e nao uma anterior aberta antes da troca.
 */
public record AceitePrivacidadeRequest(
        @NotBlank(message = "versao é obrigatória") String versao) {
}
