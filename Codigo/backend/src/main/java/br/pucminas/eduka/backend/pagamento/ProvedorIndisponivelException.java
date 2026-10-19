package br.pucminas.eduka.backend.pagamento;

/**
 * Timeout, erro de rede ou erro temporario do provedor. O resultado e
 * <em>inconclusivo</em>: o pedido pode ter sido aplicado ou nao, e quem chamou
 * precisa consultar antes de repetir.
 */
public class ProvedorIndisponivelException extends RuntimeException {

    public ProvedorIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public ProvedorIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
