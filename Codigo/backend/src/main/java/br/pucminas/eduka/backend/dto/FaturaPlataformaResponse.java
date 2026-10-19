package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Fatura da plataforma (Fluxo A).
 *
 * @param competencia {@code yyyy-MM}
 * @param acoes       acoes permitidas, calculadas no backend
 */
public record FaturaPlataformaResponse(
        UUID id,
        UUID escolaId,
        String escolaNome,
        String competencia,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        String status,
        String situacaoPagamento,
        FormaPagamento formaPagamento,
        PagamentoResumoResponse pagamento,
        List<String> acoes) {
}
