package br.pucminas.eduka.backend.dto.academico;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;

/**
 * Matriculas da turma (ou do aluno) filtrada, sem o filtro de situacao: os
 * contadores dos filtros da tela Matriculas (IU11) e as vagas ocupadas.
 *
 * @param porSituacao   todas as situacoes, com zero quando nao ha matricula nela
 * @param paraMatricular so na consulta por turma (sem aluno): alunos da escola,
 *                      sem os anonimizados e os inativos, que ainda nao tem
 *                      registro na turma e podem entrar nela; nulo nas outras
 *                      consultas
 */
public record ResumoMatriculasResponse(long total, Map<StatusMatricula, Long> porSituacao, Long paraMatricular) {

    public static ResumoMatriculasResponse de(Collection<StatusMatricula> situacoes, Long paraMatricular) {
        Map<StatusMatricula, Long> porSituacao = new EnumMap<>(StatusMatricula.class);
        for (StatusMatricula situacao : StatusMatricula.values()) {
            porSituacao.put(situacao, 0L);
        }
        situacoes.forEach(situacao -> porSituacao.merge(situacao, 1L, Long::sum));
        return new ResumoMatriculasResponse(situacoes.size(), porSituacao, paraMatricular);
    }
}
