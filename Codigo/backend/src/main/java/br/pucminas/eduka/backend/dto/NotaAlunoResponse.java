package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.StatusMatricula;

/**
 * Uma linha do diario de notas de uma avaliacao: o aluno e a nota dele, que
 * vem nula enquanto nao foi lancada.
 *
 * @param situacaoMatricula situacao da matricula do aluno na turma; nula se
 *                          ele tem nota mas ja nao esta mais na turma
 *                          (transferido)
 */
public record NotaAlunoResponse(
        UUID alunoId,
        String alunoNome,
        String alunoNumeroMatricula,
        StatusMatricula situacaoMatricula,
        BigDecimal valor,
        String observacao) {
}
