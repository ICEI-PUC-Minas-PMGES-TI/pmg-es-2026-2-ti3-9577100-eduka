package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Fatura que a escola paga ao Eduka (Fluxo A), lancada pelo Super Admin.
 *
 * @param competencia mes de referencia, {@code yyyy-MM}
 */
public record LancarFaturaRequest(
        @NotNull(message = "competencia é obrigatória")
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "competencia deve estar no formato yyyy-MM") String competencia,
        @NotBlank(message = "descricao é obrigatória")
        @Size(max = 255, message = "descricao deve ter no máximo 255 caracteres") String descricao,
        @NotNull(message = "valor é obrigatório")
        @Positive(message = "valor deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "valor deve ter até 8 dígitos inteiros e 2 decimais") BigDecimal valor,
        @NotNull(message = "dataVencimento é obrigatória") LocalDate dataVencimento) {
}
