package br.pucminas.eduka.backend.dto;

public record LoginRequest(String login, String senha) {

    /** Impede que a senha apareca em log ou mensagem de erro. */
    @Override
    public String toString() {
        return "LoginRequest[login=%s]".formatted(login);
    }
}
