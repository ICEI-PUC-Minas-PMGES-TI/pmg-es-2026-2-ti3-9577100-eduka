package br.pucminas.eduka.backend.exception;

/** Numero de matricula repetido dentro da mesma escola (UNIQUE de V5). */
public class NumeroMatriculaJaCadastradoException extends RuntimeException {

    public NumeroMatriculaJaCadastradoException() {
        super("Ja existe aluno com este numero de matricula nesta escola");
    }
}
