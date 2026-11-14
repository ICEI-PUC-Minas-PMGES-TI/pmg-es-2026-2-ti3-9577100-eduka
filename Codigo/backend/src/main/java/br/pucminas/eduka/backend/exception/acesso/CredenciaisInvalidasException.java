package br.pucminas.eduka.backend.exception.acesso;

/**
 * Lancada para login inexistente, senha errada e usuario inativo, sempre com a
 * mesma mensagem: distinguir os casos permitiria descobrir quais logins existem.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Login ou senha invalidos");
    }
}
