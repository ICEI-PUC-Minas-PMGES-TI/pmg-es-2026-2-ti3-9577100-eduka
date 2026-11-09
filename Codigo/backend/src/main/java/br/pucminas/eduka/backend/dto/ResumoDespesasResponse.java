package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import br.pucminas.eduka.backend.domain.SituacaoDespesa;

/**
 * Despesas por situacao, com os filtros da listagem exceto o de situacao
 * (periodo e categoria valem), para os cards da tela Despesas (IU21).
 *
 * @param porSituacao uma entrada por situacao, sempre todas e na ordem de
 *                    {@link SituacaoDespesa}
 */
public record ResumoDespesasResponse(List<TotalPorSituacao> porSituacao) {

    public record TotalPorSituacao(SituacaoDespesa situacao, long quantidade, BigDecimal valor) {
    }
}
