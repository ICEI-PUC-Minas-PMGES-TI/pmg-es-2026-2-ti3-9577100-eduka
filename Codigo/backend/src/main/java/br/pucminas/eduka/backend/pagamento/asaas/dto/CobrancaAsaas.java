package br.pucminas.eduka.backend.pagamento.asaas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cobranca ({@code payment}) do Asaas, nas respostas e nos eventos. Os links
 * {@code invoiceUrl} e {@code bankSlipUrl} sao descartados de proposito: o
 * pagamento acontece na tela do Eduka.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CobrancaAsaas(
        String id,
        String externalReference,
        String status,
        String billingType,
        Boolean deleted,
        BigDecimal value,
        BigDecimal netValue,
        LocalDate paymentDate,
        LocalDate clientPaymentDate,
        LocalDate confirmedDate,
        LocalDate creditDate,
        String pixTransaction,
        String nossoNumero,
        String transactionReceiptUrl) {

    public boolean removida() {
        return Boolean.TRUE.equals(deleted);
    }
}
