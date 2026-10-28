package br.pucminas.eduka.backend.exception;

/** So ha comprovante depois do pagamento registrado (R28). */
public class ComprovanteIndisponivelException extends RuntimeException {

    public ComprovanteIndisponivelException() {
        super("Ainda nao ha comprovante para este pagamento");
    }
}
