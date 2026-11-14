package br.pucminas.eduka.backend.exception.academico;

/**
 * O aluno ja tem matricula nesta turma, em qualquer situacao. Para voltar a
 * uma turma trancada, reativar a matricula existente.
 */
public class MatriculaJaExisteException extends RuntimeException {

    public MatriculaJaExisteException() {
        super("Aluno ja possui matricula nesta turma");
    }
}
