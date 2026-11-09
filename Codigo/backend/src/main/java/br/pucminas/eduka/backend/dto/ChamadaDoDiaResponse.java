package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * O que a tela de chamada precisa para um dia.
 *
 * @param alunos quem entra na chamada: matricula ativa na turma, feita ate o
 *               dia, pelo nome
 * @param aulas  aulas ja registradas nesse dia, da primeira para a ultima
 */
public record ChamadaDoDiaResponse(
        LocalDate data,
        List<AlunoDaChamadaResponse> alunos,
        List<AulaResumoResponse> aulas) {
}
