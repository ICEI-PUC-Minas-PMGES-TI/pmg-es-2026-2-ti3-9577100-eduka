package br.pucminas.eduka.backend.dto.academico;

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
 * os demais ficam como estao. Em rascunho as notas ficam gravadas, mas o aluno
 * so as ve quando o professor lanca (publica).
 */
public record LancarNotasRequest(
        @NotNull(message = "notas é obrigatório") List<@Valid @NotNull NotaLancamento> notas,
        /** Verdadeiro em Salvar rascunho: grava sem publicar. Nulo ou falso publica (Lancar notas). */
        Boolean rascunho) {

    public LancarNotasRequest(List<NotaLancamento> notas) {
        this(notas, null);
    }

    public boolean ehRascunho() {
        return Boolean.TRUE.equals(rascunho);
    }

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
