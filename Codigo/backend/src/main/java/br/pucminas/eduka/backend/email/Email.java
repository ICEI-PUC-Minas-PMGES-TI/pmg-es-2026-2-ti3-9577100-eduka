package br.pucminas.eduka.backend.email;

/**
 * Mensagem pronta para envio. {@code texto} e a versao sem HTML, para
 * clientes de e-mail que nao renderizam HTML (e para filtros de spam, que
 * desconfiam de e-mail so com HTML).
 */
public record Email(
        String destinatario,
        String nomeDestinatario,
        String assunto,
        String html,
        String texto) {

    /** Destinatario e conteudo sao dado pessoal: fora de log. */
    @Override
    public String toString() {
        return "Email[assunto=%s]".formatted(assunto);
    }
}
