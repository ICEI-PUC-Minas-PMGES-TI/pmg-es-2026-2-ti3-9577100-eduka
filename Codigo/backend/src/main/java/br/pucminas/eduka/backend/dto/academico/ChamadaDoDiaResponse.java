package br.pucminas.eduka.backend.dto.academico;

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
        /** A disciplina tem aula na grade da turma nesse dia da semana: so entao da para registrar aula nova. */
        boolean diaDeAula,
        List<AlunoDaChamadaResponse> alunos,
        List<AulaResumoResponse> aulas) {
}
