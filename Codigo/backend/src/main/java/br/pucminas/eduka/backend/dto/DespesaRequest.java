package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lanca uma despesa da escola. Sem {@code escolaId}: a escola vem do token, e
 * categoria de outra escola responde 404.
 *
 * @param paga opcional; {@code true} lanca a despesa ja como PAGA (campo "Ja
 *             esta paga" da IU21). Padrao: A_PAGAR
 */
public record DespesaRequest(
        @NotBlank(message = "descricao é obrigatória")
        @Size(max = 255, message = "descricao deve ter no máximo 255 caracteres") String descricao,
        @NotNull(message = "categoriaId é obrigatório") UUID categoriaId,
        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "valor deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "valor deve ter até 8 dígitos inteiros e 2 decimais") BigDecimal valor,
        @NotNull(message = "dataVencimento é obrigatória") LocalDate dataVencimento,
        Boolean paga) {
}
