package br.pucminas.eduka.backend.exception;

/** O codigo passou da validade; a verificacao continua aberta para um reenvio. */
public class CodigoVerificacaoExpiradoException extends RuntimeException {

    public CodigoVerificacaoExpiradoException() {
        super("Codigo de verificacao expirado. Solicite um novo codigo");
    }
}
