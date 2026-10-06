package br.pucminas.eduka.backend.exception;

/** Reenvio pedido antes do intervalo minimo ou depois do limite de envios. */
public class ReenvioCodigoIndisponivelException extends RuntimeException {

    public ReenvioCodigoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
