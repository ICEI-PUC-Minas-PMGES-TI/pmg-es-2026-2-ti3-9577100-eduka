package br.pucminas.eduka.backend.dto.academico;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.pucminas.eduka.backend.domain.academico.TipoAvaliacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edicao de avaliacao: substitui os dados inteiros. Turma e disciplina nao
 * mudam (ficam fora do corpo de proposito).
 */
public record AtualizarAvaliacaoRequest(
        @NotNull(message = "tipo é obrigatório") TipoAvaliacao tipo,
        @NotBlank(message = "titulo é obrigatório")
        @Size(max = 150, message = "titulo deve ter no máximo 150 caracteres") String titulo,
        @Size(max = 500, message = "descricao deve ter no máximo 500 caracteres") String descricao,
        @NotNull(message = "data é obrigatória") LocalDate data,
        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "valor deve ser maior que zero")
        @Digits(integer = 3, fraction = 2, message = "valor deve ter até 3 dígitos inteiros e 2 decimais") BigDecimal valor) {
}
