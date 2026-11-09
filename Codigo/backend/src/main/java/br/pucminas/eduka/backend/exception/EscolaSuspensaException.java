package br.pucminas.eduka.backend.exception;

import br.pucminas.eduka.backend.domain.MotivoSuspensao;

/**
 * Usuario de escola suspensa (RF24). No login, so e lancada depois de a senha
 * conferir: quem nao a conhece nao descobre a suspensao. Com o token ja
 * emitido, sai de qualquer requisicao ({@code EscolaSuspensaFilter}).
 */
public class EscolaSuspensaException extends RuntimeException {

    /** Nulo quando nao se sabe. */
    private final transient MotivoSuspensao motivo;

    public EscolaSuspensaException() {
        this(null);
    }

    public EscolaSuspensaException(MotivoSuspensao motivo) {
        super("A assinatura da escola esta suspensa. Fale com a administracao da escola ou com o suporte do Eduka");
        this.motivo = motivo;
    }

    public MotivoSuspensao getMotivo() {
        return motivo;
    }
}
