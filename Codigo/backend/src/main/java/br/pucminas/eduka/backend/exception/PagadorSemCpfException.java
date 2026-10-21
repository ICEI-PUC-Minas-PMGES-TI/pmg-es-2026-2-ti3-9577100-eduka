package br.pucminas.eduka.backend.exception;

/** Quem paga a cobranca nao tem CPF valido no cadastro (R18). */
public class PagadorSemCpfException extends RuntimeException {

    public PagadorSemCpfException() {
        super("O cadastro de quem paga esta sem CPF valido. Procure a secretaria");
    }
}
