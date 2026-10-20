package br.pucminas.eduka.backend.exception;

/** A escola ainda nao pode receber pagamentos online (R11, R12). */
public class RecebimentosIndisponiveisException extends RuntimeException {

    /** @param motivo um dos motivos do R11 (NAO_INICIADA, AGUARDANDO_APROVACAO...) */
    public RecebimentosIndisponiveisException(String motivo) {
        super("A escola ainda nao esta recebendo pagamentos online (" + motivo + ")");
    }
}
