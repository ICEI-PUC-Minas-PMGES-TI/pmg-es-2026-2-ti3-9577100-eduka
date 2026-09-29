package br.pucminas.eduka.backend.exception;

/** CPF repetido dentro da mesma escola. A mensagem nao ecoa o CPF (dado pessoal). */
public class CpfJaCadastradoException extends RuntimeException {

    public CpfJaCadastradoException() {
        this("Ja existe professor com este CPF nesta escola");
    }

    public CpfJaCadastradoException(String mensagem) {
        super(mensagem);
    }
}
