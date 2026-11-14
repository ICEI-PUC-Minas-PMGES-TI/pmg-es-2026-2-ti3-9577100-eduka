package br.pucminas.eduka.backend.dto.academico;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;

/**
 * Uma linha da chamada de uma aula.
 *
 * @param situacaoMatricula situacao da matricula do aluno na turma hoje; nula
 *                          se ele esteve na aula mas ja nao esta mais na turma
 *                          (transferido)
 */
public record FrequenciaAlunoResponse(
        UUID alunoId,
        String alunoNome,
        String alunoNumeroMatricula,
        StatusMatricula situacaoMatricula,
        boolean presente) {
}
