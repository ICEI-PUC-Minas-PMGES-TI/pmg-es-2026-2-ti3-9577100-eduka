package br.pucminas.eduka.backend.exception.academico;

/** Avaliacao com nota lancada nao e excluida: as notas sao historico do aluno. */
public class AvaliacaoComNotasException extends RuntimeException {

    public AvaliacaoComNotasException() {
        super("Avaliacao ja tem notas lancadas e nao pode ser excluida");
    }
}
