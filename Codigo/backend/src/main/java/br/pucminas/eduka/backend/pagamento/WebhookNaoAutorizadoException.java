package br.pucminas.eduka.backend.pagamento;

/** Aviso do provedor sem o token do webhook, ou com outro token (HTTP 401). */
public class WebhookNaoAutorizadoException extends RuntimeException {

    public WebhookNaoAutorizadoException() {
        super("Aviso do provedor de pagamento nao autenticado");
    }
}
