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
 * Edicao de uma cobranca em aberto. Substitui todos os campos: matricula ou
 * responsavel que vierem nulos sao removidos da cobranca. O aluno nao muda;
 * cobranca lancada para o aluno errado e cancelada e refeita.
 */
public record AtualizarCobrancaRequest(
        UUID matriculaId,
        UUID responsavelId,
        @NotBlank(message = "descricao é obrigatória")
        @Size(max = 255, message = "descricao deve ter no máximo 255 caracteres") String descricao,
        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "valor deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "valor deve ter até 8 dígitos inteiros e 2 decimais") BigDecimal valor,
        @NotNull(message = "dataVencimento é obrigatória") LocalDate dataVencimento) {
}
