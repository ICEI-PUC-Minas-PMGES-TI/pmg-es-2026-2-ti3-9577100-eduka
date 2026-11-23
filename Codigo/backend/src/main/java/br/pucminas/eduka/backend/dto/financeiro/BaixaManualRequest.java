package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.domain.financeiro.FormaPagamentoManual;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Baixa manual (UC12): o pagamento que a escola recebeu fora do Eduka.
 *
 * @param valorPago     o que entrou; pode ser diferente do valor da cobranca
 *                      (desconto, juros)
 * @param dataPagamento dia em que a escola recebeu, no fuso dela; nao pode ser
 *                      futuro (conferido no servico, que sabe o dia da escola)
 */
public record BaixaManualRequest(
        @NotNull(message = "valorPago é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "valorPago deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "valorPago deve ter até 8 dígitos inteiros e 2 decimais") BigDecimal valorPago,
        @NotNull(message = "dataPagamento é obrigatória") LocalDate dataPagamento,
        @NotNull(message = "forma é obrigatória") FormaPagamentoManual forma) {
}
