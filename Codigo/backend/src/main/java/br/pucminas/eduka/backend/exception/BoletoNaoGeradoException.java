package br.pucminas.eduka.backend.exception;

/**
 * O PDF do boleto so existe depois que a cobranca foi emitida no provedor como
 * boleto (Pagar com a forma BOLETO).
 */
public class BoletoNaoGeradoException extends RuntimeException {

    public BoletoNaoGeradoException() {
        super("O boleto desta cobranca ainda nao foi gerado");
    }
}
