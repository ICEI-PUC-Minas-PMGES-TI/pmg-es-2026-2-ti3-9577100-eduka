package br.pucminas.eduka.backend.exception.acesso;

public class SenhaInvalidaException extends RuntimeException {

    public SenhaInvalidaException(String motivo) {
        super(motivo);
    }
}
