package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Uma linha da chamada de uma aula: o aluno e se esteve presente.
 *
 * @param presente {@code false} e a falta
 */
public record ChamadaItem(
        @NotNull(message = "alunoId é obrigatório") UUID alunoId,
        @NotNull(message = "presente é obrigatório") Boolean presente) {
}
