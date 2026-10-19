package br.pucminas.eduka.backend.dto;

import java.time.OffsetDateTime;

public record PixResponse(String qrCodeBase64, String copiaECola, OffsetDateTime validade) {
}
