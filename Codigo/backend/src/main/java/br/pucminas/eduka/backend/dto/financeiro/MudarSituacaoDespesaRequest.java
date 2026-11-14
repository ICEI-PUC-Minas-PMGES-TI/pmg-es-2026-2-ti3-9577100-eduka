package br.pucminas.eduka.backend.dto.financeiro;

import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;
import jakarta.validation.constraints.NotNull;

/** Nova situacao da despesa: PAGA, A_PAGAR (desfaz o pagamento) ou CANCELADA. */
public record MudarSituacaoDespesaRequest(
        @NotNull(message = "situacao é obrigatória") SituacaoDespesa situacao) {
}
