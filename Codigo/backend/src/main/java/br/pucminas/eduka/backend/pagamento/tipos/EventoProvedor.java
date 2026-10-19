package br.pucminas.eduka.backend.pagamento.tipos;

import java.time.LocalDate;

/**
 * Aviso do provedor ja traduzido. {@code cobranca} e nula em evento de conta
 * e {@code situacaoConta} e nula em evento de cobranca (ou quando o payload
 * nao traz a situacao: o roteador consulta). {@code payloadSaneado} e o JSON
 * recebido sem dados pessoais nem bancarios.
 */
public record EventoProvedor(
        String idEvento,
        TipoEventoProvedor tipo,
        String tipoOriginal,
        String idContaExterna,
        CobrancaProvedor cobranca,
        SituacaoContaProvedor situacaoConta,
        LocalDate dataEvento,
        String payloadSaneado) {
}
