package br.pucminas.eduka.backend.email;

/**
 * O provedor recusou o e-mail de um jeito que tentar de novo nao resolve
 * (ex.: endereco invalido, corpo malformado). A fila de notificacoes marca a
 * notificacao como FALHOU na hora, sem novas tentativas. Qualquer outra
 * excecao de {@link EmailSender#enviar} e tratada como falha temporaria.
 *
 * <p>
 * A mensagem leva so o status HTTP: o corpo da resposta pode ecoar o
 * destinatario.
 * </p>
 */
public class EmailRecusadoException extends RuntimeException {

    private final int status;

    public EmailRecusadoException(int status) {
        super("Provedor de e-mail recusou o envio (HTTP %d)".formatted(status));
        this.status = status;
    }

    public int status() {
        return status;
    }
}
