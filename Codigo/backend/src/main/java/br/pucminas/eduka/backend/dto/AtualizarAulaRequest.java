package br.pucminas.eduka.backend.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Correcao de uma aula: substitui o conteudo e a chamada. Turma, disciplina e
 * data nao mudam (ficam fora do corpo de proposito).
 */
public record AtualizarAulaRequest(
        @NotBlank(message = "conteudo é obrigatório")
        @Size(max = 2000, message = "conteudo deve ter no máximo 2000 caracteres") String conteudo,
        @NotNull(message = "chamada é obrigatória") List<@Valid @NotNull ChamadaItem> chamada) {
}
