package br.pucminas.eduka.backend.exception;

/** O aluno ja tem um pedido de correcao aberto (IU15: uma solicitacao aberta por vez). */
public class SolicitacaoCorrecaoAbertaException extends RuntimeException {

    public SolicitacaoCorrecaoAbertaException() {
        super("Ja existe um pedido de correcao aberto. Aguarde a secretaria atender");
    }
}
