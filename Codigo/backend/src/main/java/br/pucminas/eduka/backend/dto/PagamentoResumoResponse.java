package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

public record PagamentoResumoResponse(BigDecimal valorPago, LocalDate dataPagamento, FormaPagamento forma) {
}
