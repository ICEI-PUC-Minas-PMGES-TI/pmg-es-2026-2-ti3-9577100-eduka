package br.pucminas.eduka.backend.exception.comum;

/**
 * Exclusao recusada porque outro cadastro depende do registro (ex.: periodo
 * letivo com turmas). A mensagem diz o que impede, para a secretaria saber o
 * que resolver antes.
 */
public class RegistroEmUsoException extends RuntimeException {

    public RegistroEmUsoException(String mensagem) {
        super(mensagem);
    }
}
