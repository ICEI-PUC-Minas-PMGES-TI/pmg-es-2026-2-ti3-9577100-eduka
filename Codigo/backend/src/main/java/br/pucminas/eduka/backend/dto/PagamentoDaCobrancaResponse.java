package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Pagamento de uma cobranca paga, como aparece no historico e na listagem.
 * Nada do provedor (URL do recibo, id externo) sai aqui.
 *
 * @param origem         ONLINE (confirmado pelo provedor) ou MANUAL (baixa
 *                       feita pela escola)
 * @param valorPago      na baixa manual, o valor da cobranca
 * @param forma          nula na baixa manual
 * @param comprovanteUrl caminho da API que devolve o comprovante (R28 a R30),
 *                       ja no perfil de quem pediu; nulo na baixa manual, que
 *                       nao tem comprovante
 * @param valorEstornado quanto do pago foi devolvido; zero se nada, e sempre na
 *                       baixa manual, que nao passa pelo provedor
 */
public record PagamentoDaCobrancaResponse(
        OrigemPagamento origem,
        BigDecimal valorPago,
        LocalDate dataPagamento,
        FormaPagamento forma,
        String comprovanteUrl,
        BigDecimal valorEstornado) {

    public static PagamentoDaCobrancaResponse online(PagamentoCobranca pagamento, String comprovanteUrl) {
        return new PagamentoDaCobrancaResponse(pagamento.getOrigem(), pagamento.getValorPago(),
                pagamento.getDataPagamento(), pagamento.getFormaPagamento(), comprovanteUrl,
                pagamento.getValorEstornado());
    }

    public static PagamentoDaCobrancaResponse manual(BigDecimal valor, LocalDate dataPagamento) {
        return new PagamentoDaCobrancaResponse(OrigemPagamento.MANUAL, valor, dataPagamento, null, null,
                BigDecimal.ZERO);
    }
}
