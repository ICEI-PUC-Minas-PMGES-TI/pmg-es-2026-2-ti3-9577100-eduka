package br.pucminas.eduka.backend.exception.pessoas;

/**
 * Exclusao barrada porque outros registros (turmas, avaliacoes...) ainda
 * apontam para o professor. Nesse caso o caminho e inativar.
 */
public class ProfessorComHistoricoException extends RuntimeException {

    public ProfessorComHistoricoException() {
        super("Professor possui historico na escola e nao pode ser excluido; inative-o");
    }
}
