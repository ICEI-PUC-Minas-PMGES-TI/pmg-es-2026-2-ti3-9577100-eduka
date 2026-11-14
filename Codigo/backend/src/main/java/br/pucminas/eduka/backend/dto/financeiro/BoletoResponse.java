package br.pucminas.eduka.backend.dto.financeiro;

/** Boleto sem link de PDF: so a linha digitavel e o codigo de barras. */
public record BoletoResponse(String linhaDigitavel, String codigoBarras) {
}
