package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * O que a tela precisa para o pagador pagar, sem sair do Eduka.
 *
 * @param situacao PRONTO ou PROCESSANDO
 * @param pix      so quando forma = PIX e situacao = PRONTO
 * @param boleto   so quando forma = BOLETO e situacao = PRONTO
 */
public record DadosPagamentoResponse(
        String situacao,
        FormaPagamento forma,
        BigDecimal valor,
        LocalDate vencimento,
        PixResponse pix,
        BoletoResponse boleto) {
}
