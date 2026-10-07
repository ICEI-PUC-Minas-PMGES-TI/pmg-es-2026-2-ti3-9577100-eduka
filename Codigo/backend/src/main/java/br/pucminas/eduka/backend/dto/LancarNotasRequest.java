package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lancamento de notas de uma avaliacao, em lote. So os alunos enviados mudam;
 * os demais ficam como estao.
 */
public record LancarNotasRequest(
        @NotNull(message = "notas é obrigatório") List<@Valid @NotNull NotaLancamento> notas) {

    /**
     * @param valor de 0 ate o valor da avaliacao; nulo apaga a nota ja lancada
     */
    public record NotaLancamento(
            @NotNull(message = "alunoId é obrigatório") UUID alunoId,
            @DecimalMin(value = "0", message = "a nota não pode ser negativa")
            @Digits(integer = 3, fraction = 2, message = "a nota deve ter até 3 dígitos inteiros e 2 decimais") BigDecimal valor,
            @Size(max = 500, message = "observacao deve ter no máximo 500 caracteres") String observacao) {
    }
}
