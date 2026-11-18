package br.pucminas.eduka.backend.dto.financeiro;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import jakarta.validation.constraints.NotNull;

/** Forma escolhida na tela antes de clicar em Pagar. */
public record PagarRequest(
        @NotNull(message = "Forma de pagamento e obrigatoria") FormaPagamento forma) {
}
