package br.pucminas.eduka.backend.security;

/** Falha de autenticacao do AES-GCM: dado adulterado ou cifrado com outra chave. */
public class PiiDecryptionException extends RuntimeException {

    public PiiDecryptionException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
