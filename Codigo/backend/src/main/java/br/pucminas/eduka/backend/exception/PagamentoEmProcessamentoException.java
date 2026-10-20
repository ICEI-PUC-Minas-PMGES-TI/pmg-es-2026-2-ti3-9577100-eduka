package br.pucminas.eduka.backend.exception;

/**
 * Outro pedido ja esta gerando o pagamento desta cobranca no provedor
 * (situacao EMITINDO). A tela tenta de novo em alguns segundos.
 */
public class PagamentoEmProcessamentoException extends RuntimeException {

    public PagamentoEmProcessamentoException() {
        super("O pagamento desta cobranca esta sendo gerado. Tente de novo em alguns segundos");
    }
}
