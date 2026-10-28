package br.pucminas.eduka.backend.pagamento.tipos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cobranca como o provedor a ve. Nao tem link de fatura nem de boleto: o
 * pagamento acontece na tela do Eduka. {@code urlReciboProvedor} e so para
 * auditoria e nunca sai do backend.
 */
public record CobrancaProvedor(
        String idCobrancaExterna,
        String referenciaExterna,
        StatusCobrancaProvedor status,
        FormaPagamento forma,
        BigDecimal valor,
        BigDecimal valorPago,
        BigDecimal valorLiquido,
        LocalDate dataPagamento,
        LocalDate dataCredito,
        String idTransacao,
        String urlReciboProvedor) {
}
