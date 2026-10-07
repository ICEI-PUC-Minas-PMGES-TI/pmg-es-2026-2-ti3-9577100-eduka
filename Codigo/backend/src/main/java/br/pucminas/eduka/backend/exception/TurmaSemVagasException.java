package br.pucminas.eduka.backend.exception;

/** Todas as vagas da turma estao ocupadas por matriculas ativas. */
public class TurmaSemVagasException extends RuntimeException {

    public TurmaSemVagasException() {
        super("Turma sem vagas disponiveis");
    }
}
