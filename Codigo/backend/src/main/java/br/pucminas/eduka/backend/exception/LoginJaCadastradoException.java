package br.pucminas.eduka.backend.exception;

public class LoginJaCadastradoException extends RuntimeException {

    public LoginJaCadastradoException(String login) {
        super("Ja existe usuario com o login '%s'".formatted(login));
    }
}
