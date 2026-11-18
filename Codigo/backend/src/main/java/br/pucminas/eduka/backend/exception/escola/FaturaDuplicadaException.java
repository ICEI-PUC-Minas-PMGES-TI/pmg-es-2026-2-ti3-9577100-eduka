package br.pucminas.eduka.backend.exception.escola;

/** Ja existe fatura nao cancelada dessa escola nessa competencia (R26). */
public class FaturaDuplicadaException extends RuntimeException {

    public FaturaDuplicadaException() {
        super("Esta escola ja tem uma fatura nesta competencia");
    }
}
