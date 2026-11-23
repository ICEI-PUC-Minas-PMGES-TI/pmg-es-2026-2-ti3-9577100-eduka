package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edicao de uma despesa. Substitui todos os campos; a situacao muda so por
 * {@link MudarSituacaoDespesaRequest}. O responsavel e exigido tambem aqui:
 * despesa antiga, sem ele, ganha um na primeira edicao.
 */
public record AtualizarDespesaRequest(
        @NotBlank(message = "descricao é obrigatória")
        @Size(max = 255, message = "descricao deve ter no máximo 255 caracteres") String descricao,
        @NotBlank(message = "responsavel é obrigatório")
        @Size(max = 120, message = "responsavel deve ter no máximo 120 caracteres") String responsavel,
        @NotNull(message = "categoriaId é obrigatório") UUID categoriaId,
        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "valor deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "valor deve ter até 8 dígitos inteiros e 2 decimais") BigDecimal valor,
        @NotNull(message = "dataVencimento é obrigatória") LocalDate dataVencimento) {
}
