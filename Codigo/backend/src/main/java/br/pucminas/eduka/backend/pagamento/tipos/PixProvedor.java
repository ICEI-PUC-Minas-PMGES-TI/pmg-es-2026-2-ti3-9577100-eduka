package br.pucminas.eduka.backend.pagamento.tipos;

import java.time.OffsetDateTime;

public record PixProvedor(String qrCodeBase64, String copiaECola, OffsetDateTime validade) {
}
