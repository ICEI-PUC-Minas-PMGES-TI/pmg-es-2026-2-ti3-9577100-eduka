package br.pucminas.eduka.backend.pagamento.asaas.dto;

import java.time.LocalDate;

/** Corpo de {@code PUT /v3/payments/{id}}: troca a forma e o vencimento na mesma cobranca. */
public record AlteracaoCobrancaAsaas(String billingType, LocalDate dueDate) {
}
