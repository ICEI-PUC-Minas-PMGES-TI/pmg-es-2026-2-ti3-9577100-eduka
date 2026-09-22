package br.pucminas.eduka.backend.exception;

public class CnpjJaCadastradoException extends RuntimeException {

    public CnpjJaCadastradoException(String cnpj) {
        super("Ja existe escola com o CNPJ '%s'".formatted(cnpj));
    }
}
