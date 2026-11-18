package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.domain.financeiro.FormaPagamentoManual;
import br.pucminas.eduka.backend.domain.financeiro.OrigemPagamento;
import br.pucminas.eduka.backend.domain.financeiro.PagamentoCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Pagamento de uma cobranca paga, como aparece no historico e na listagem.
 * Nada do provedor (URL do recibo, id externo) sai aqui.
 *
 * @param origem         ONLINE (confirmado pelo provedor) ou MANUAL (baixa
 *                       feita pela escola)
 * @param valorPago      na baixa manual, o informado pela escola
 * @param forma          PIX ou BOLETO do provedor; nula na baixa manual
 * @param formaManual    como a escola recebeu, na baixa manual; nula no online
 *                       e nas baixas feitas antes de a forma ser pedida
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
        FormaPagamentoManual formaManual,
        String comprovanteUrl,
        BigDecimal valorEstornado) {

    public static PagamentoDaCobrancaResponse online(PagamentoCobranca pagamento, String comprovanteUrl) {
        return new PagamentoDaCobrancaResponse(pagamento.getOrigem(), pagamento.getValorPago(),
                pagamento.getDataPagamento(), pagamento.getFormaPagamento(), null, comprovanteUrl,
                pagamento.getValorEstornado());
    }

    public static PagamentoDaCobrancaResponse manual(BigDecimal valorPago, LocalDate dataPagamento,
            FormaPagamentoManual forma) {
        return new PagamentoDaCobrancaResponse(OrigemPagamento.MANUAL, valorPago, dataPagamento, null, forma, null,
                BigDecimal.ZERO);
    }
}
