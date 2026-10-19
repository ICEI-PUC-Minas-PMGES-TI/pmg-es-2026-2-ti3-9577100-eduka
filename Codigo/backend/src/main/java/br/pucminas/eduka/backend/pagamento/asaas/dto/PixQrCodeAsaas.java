package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code GET /v3/payments/{id}/pixQrCode}. {@code expirationDate} vem como
 * {@code yyyy-MM-dd HH:mm:ss}, no horario de Brasilia.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PixQrCodeAsaas(String encodedImage, String payload, String expirationDate) {
}
