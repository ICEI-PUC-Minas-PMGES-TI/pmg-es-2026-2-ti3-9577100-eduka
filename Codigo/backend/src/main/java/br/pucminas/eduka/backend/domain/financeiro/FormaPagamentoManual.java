package br.pucminas.eduka.backend.domain.financeiro;

/**
 * Como o dinheiro chegou a escola na baixa manual (coluna
 * {@code FORMA_PAGAMENTO_MANUAL} de {@code COBRANCA}, V49): pagamento feito
 * fora do Eduka. PIX e boleto aqui sao os recebidos direto na conta da escola,
 * sem passar pelo provedor; os do provedor ficam em
 * {@link br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento}.
 */
public enum FormaPagamentoManual {
    DINHEIRO,
    PIX,
    TRANSFERENCIA,
    CARTAO,
    BOLETO
}
