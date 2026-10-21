package br.pucminas.eduka.backend.pagamento.tipos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NovaCobrancaProvedor(
        String idCliente,
        FormaPagamento forma,
        BigDecimal valor,
        LocalDate vencimento,
        String descricao,
        String referenciaExterna) {
}
