package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;

/** Pagamento registrado de uma cobranca (PAGAMENTO_COBRANCA), sem nada do provedor. */
public record PagamentoResumoResponse(BigDecimal valorPago, LocalDate dataPagamento, FormaPagamento forma) {

    public static PagamentoResumoResponse de(PagamentoCobranca pagamento) {
        return new PagamentoResumoResponse(pagamento.getValorPago(), pagamento.getDataPagamento(),
                pagamento.getFormaPagamento());
    }
}
