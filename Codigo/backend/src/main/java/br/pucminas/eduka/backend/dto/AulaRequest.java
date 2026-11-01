package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro de uma aula com a chamada, pelo professor. Sem {@code escolaId} nem
 * {@code professorId}: os dois vem do token, e o professor precisa estar
 * alocado na turma/disciplina.
 *
 * @param data    dia da aula; nao pode ser futuro
 * @param chamada todos os alunos com matricula ativa na turma, cada um uma vez
 */
public record AulaRequest(
        @NotNull(message = "turmaId é obrigatório") Long turmaId,
        @NotNull(message = "disciplinaId é obrigatório") Long disciplinaId,
        @NotNull(message = "data é obrigatória") LocalDate data,
        @NotBlank(message = "conteudo é obrigatório")
        @Size(max = 2000, message = "conteudo deve ter no máximo 2000 caracteres") String conteudo,
        @NotNull(message = "chamada é obrigatória") List<@Valid @NotNull ChamadaItem> chamada) {
}
