package br.pucminas.eduka.backend.exception;

/** Atalho de teste pedido com o provedor fora do Sandbox (NOVA-6). */
public class SandboxIndisponivelException extends RuntimeException {

    public SandboxIndisponivelException() {
        super("Atalho disponivel so com o provedor de pagamento no Sandbox");
    }
}
