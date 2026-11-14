package br.pucminas.eduka.backend.dto.financeiro;

import java.time.OffsetDateTime;

public record PixResponse(String qrCodeBase64, String copiaECola, OffsetDateTime validade) {
}
