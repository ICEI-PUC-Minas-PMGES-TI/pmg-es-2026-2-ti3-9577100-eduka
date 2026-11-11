package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * O que a tela precisa para o pagador pagar, sem sair do Eduka.
 *
 * @param situacao      PRONTO ou PROCESSANDO
 * @param pix           so quando forma = PIX e situacao = PRONTO
 * @param boleto        so quando forma = BOLETO e situacao = PRONTO
 * @param recebedorNome quem recebe o dinheiro (a escola, no pagamento do
 *                      aluno); nulo quando a tela nao precisa mostrar
 */
public record DadosPagamentoResponse(
        String situacao,
        FormaPagamento forma,
        BigDecimal valor,
        LocalDate vencimento,
        PixResponse pix,
        BoletoResponse boleto,
        String recebedorNome) {

    public static final String PRONTO = "PRONTO";
    public static final String PROCESSANDO = "PROCESSANDO";

    public static DadosPagamentoResponse comPix(BigDecimal valor, LocalDate vencimento, PixResponse pix) {
        return new DadosPagamentoResponse(PRONTO, FormaPagamento.PIX, valor, vencimento, pix, null, null);
    }

    public static DadosPagamentoResponse comBoleto(BigDecimal valor, LocalDate vencimento, BoletoResponse boleto) {
        return new DadosPagamentoResponse(PRONTO, FormaPagamento.BOLETO, valor, vencimento, null, boleto, null);
    }

    /** O provedor nao respondeu a tempo: os dados de pagamento ainda nao existem. */
    public static DadosPagamentoResponse emProcessamento(FormaPagamento forma, BigDecimal valor,
            LocalDate vencimento) {
        return new DadosPagamentoResponse(PROCESSANDO, forma, valor, vencimento, null, null, null);
    }

    /** A mesma resposta dizendo quem recebe o dinheiro. */
    public DadosPagamentoResponse comRecebedor(String nome) {
        return new DadosPagamentoResponse(situacao, forma, valor, vencimento, pix, boleto, nome);
    }

    public boolean pronto() {
        return PRONTO.equals(situacao);
    }
}
