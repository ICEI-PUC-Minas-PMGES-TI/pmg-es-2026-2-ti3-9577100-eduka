package br.pucminas.eduka.backend.exception;

/**
 * Login certo de usuario cuja escola esta suspensa (RF24). So e lancada
 * depois de a senha conferir: quem nao a conhece nao descobre a suspensao.
 */
public class EscolaSuspensaException extends RuntimeException {

    public EscolaSuspensaException() {
        super("A assinatura da escola esta suspensa. Fale com a administracao da escola ou com o suporte do Eduka");
    }
}
