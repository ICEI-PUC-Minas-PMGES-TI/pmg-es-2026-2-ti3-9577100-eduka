package br.pucminas.eduka.backend.exception.financeiro;

/** Nome de categoria de despesa repetido na mesma escola, sem diferenciar maiusculas. */
public class CategoriaDespesaJaExisteException extends RuntimeException {

    public CategoriaDespesaJaExisteException() {
        super("Ja existe categoria de despesa com este nome nesta escola");
    }
}
