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
 * Lanca uma cobranca para um aluno. Sem {@code escolaId}: a escola vem do
 * token, e aluno, matricula e responsavel de outra escola respondem 404.
 *
 * @param matriculaId   opcional; precisa ser uma matricula do aluno
 * @param responsavelId opcional; id da pessoa, que precisa ser responsavel do
 *                      aluno
 */
public record CobrancaRequest(
        @NotNull(message = "alunoId é obrigatório") UUID alunoId,
        UUID matriculaId,
        UUID responsavelId,
        @NotBlank(message = "descricao é obrigatória")
        @Size(max = 255, message = "descricao deve ter no máximo 255 caracteres") String descricao,
        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "valor deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "valor deve ter até 8 dígitos inteiros e 2 decimais") BigDecimal valor,
        @NotNull(message = "dataVencimento é obrigatória") LocalDate dataVencimento) {
}
