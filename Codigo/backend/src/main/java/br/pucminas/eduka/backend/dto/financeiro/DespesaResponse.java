package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.financeiro.Despesa;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;

public record DespesaResponse(
        UUID id,
        UUID categoriaId,
        String categoriaNome,
        String descricao,
        /** Nulo nas despesas lancadas antes de o campo existir. */
        String responsavel,
        BigDecimal valor,
        LocalDate dataVencimento,
        SituacaoDespesa situacao,
        Instant pagaEm,
        Instant canceladaEm,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static DespesaResponse de(Despesa despesa) {
        return new DespesaResponse(despesa.getId(), despesa.getCategoria().getId(), despesa.getCategoria().getNome(),
                despesa.getDescricao(), despesa.getResponsavel(), despesa.getValor(), despesa.getDataVencimento(), despesa.getSituacao(),
                despesa.getPagaEm(), despesa.getCanceladaEm(), despesa.getCriadoEm(), despesa.getAtualizadoEm());
    }
}
