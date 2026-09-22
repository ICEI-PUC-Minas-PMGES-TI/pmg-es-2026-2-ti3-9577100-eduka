package br.pucminas.eduka.backend.exception;

public class SenhaInvalidaException extends RuntimeException {

    public SenhaInvalidaException(String motivo) {
        super(motivo);
    }
}
