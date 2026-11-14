package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.domain.financeiro.PagamentoCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/** Pagamento registrado de uma cobranca (PAGAMENTO_COBRANCA), sem nada do provedor. */
public record PagamentoResumoResponse(BigDecimal valorPago, LocalDate dataPagamento, FormaPagamento forma,
        BigDecimal valorEstornado) {

    public static PagamentoResumoResponse de(PagamentoCobranca pagamento) {
        return new PagamentoResumoResponse(pagamento.getValorPago(), pagamento.getDataPagamento(),
                pagamento.getFormaPagamento(), pagamento.getValorEstornado());
    }
}
