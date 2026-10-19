package br.pucminas.eduka.backend.pagamento.asaas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Corpo de {@code POST /v3/payments}. */
public record NovaCobrancaAsaas(
        String customer,
        String billingType,
        BigDecimal value,
        LocalDate dueDate,
        String description,
        String externalReference) {
}
