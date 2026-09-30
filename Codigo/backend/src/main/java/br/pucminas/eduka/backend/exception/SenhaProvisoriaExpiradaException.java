package br.pucminas.eduka.backend.exception;

/**
 * So e lancada depois de a senha provisoria conferir: dizer que ela expirou
 * nao revela nada a quem nao a conhece.
 */
public class SenhaProvisoriaExpiradaException extends RuntimeException {

    public SenhaProvisoriaExpiradaException() {
        super("Senha provisoria expirada. Solicite um novo acesso a escola");
    }
}
