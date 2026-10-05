package br.pucminas.eduka.backend.exception;

/**
 * A disciplina ja tem professor nesta turma. Para trocar, desalocar o atual
 * antes.
 */
public class AlocacaoJaExisteException extends RuntimeException {

    public AlocacaoJaExisteException() {
        super("Disciplina ja possui professor alocado nesta turma");
    }
}
