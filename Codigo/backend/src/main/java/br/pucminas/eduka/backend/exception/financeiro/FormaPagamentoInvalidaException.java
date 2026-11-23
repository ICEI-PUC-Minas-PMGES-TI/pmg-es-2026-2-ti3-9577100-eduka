package br.pucminas.eduka.backend.exception.financeiro;

/** A forma de pagamento so pode ser PIX ou BOLETO (R13). */
public class FormaPagamentoInvalidaException extends RuntimeException {

    public FormaPagamentoInvalidaException() {
        super("Forma de pagamento deve ser PIX ou BOLETO");
    }
}
