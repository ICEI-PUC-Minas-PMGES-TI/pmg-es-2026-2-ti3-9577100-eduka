package br.pucminas.eduka.backend.exception;

/** Codigo errado, com a verificacao ainda aberta: da para tentar de novo. */
public class CodigoVerificacaoIncorretoException extends RuntimeException {

    public CodigoVerificacaoIncorretoException() {
        super("Codigo de verificacao incorreto");
    }
}
