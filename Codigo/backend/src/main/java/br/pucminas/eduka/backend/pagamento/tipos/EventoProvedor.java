package br.pucminas.eduka.backend.pagamento.tipos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aviso do provedor ja traduzido. {@code cobranca} e nula em evento de conta
 * e {@code situacaoConta} e nula em evento de cobranca (ou quando o payload
 * nao traz a situacao: o roteador consulta). {@code payloadSaneado} e o JSON
 * recebido sem dados pessoais nem bancarios.
 *
 * @param valorEstornado total ja devolvido deste pagamento, somando os estornos
 *                       anteriores; so no aviso de estorno, e nulo quando o
 *                       aviso nao diz quanto foi
 */
public record EventoProvedor(
        String idEvento,
        TipoEventoProvedor tipo,
        String tipoOriginal,
        String idContaExterna,
        CobrancaProvedor cobranca,
        SituacaoContaProvedor situacaoConta,
        LocalDate dataEvento,
        String payloadSaneado,
        BigDecimal valorEstornado) {

    /** Aviso que nao e de estorno. */
    public EventoProvedor(String idEvento, TipoEventoProvedor tipo, String tipoOriginal, String idContaExterna,
            CobrancaProvedor cobranca, SituacaoContaProvedor situacaoConta, LocalDate dataEvento,
            String payloadSaneado) {
        this(idEvento, tipo, tipoOriginal, idContaExterna, cobranca, situacaoConta, dataEvento, payloadSaneado, null);
    }
}
