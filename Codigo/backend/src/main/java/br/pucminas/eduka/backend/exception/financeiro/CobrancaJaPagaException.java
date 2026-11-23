package br.pucminas.eduka.backend.exception.financeiro;

/** Cancelamento recusado: o provedor ja recebeu o pagamento, que foi registrado (R25). */
public class CobrancaJaPagaException extends RuntimeException {

    public CobrancaJaPagaException() {
        super("O pagamento ja foi recebido; nao da mais para cancelar");
    }

    public CobrancaJaPagaException(String mensagem) {
        super(mensagem);
    }
}
