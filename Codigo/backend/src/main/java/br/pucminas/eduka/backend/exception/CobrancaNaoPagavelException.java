package br.pucminas.eduka.backend.exception;

/** So cobranca pendente ou em atraso pode ser paga (R12). */
public class CobrancaNaoPagavelException extends RuntimeException {

    public CobrancaNaoPagavelException() {
        super("Esta cobranca nao esta aberta para pagamento");
    }
}
