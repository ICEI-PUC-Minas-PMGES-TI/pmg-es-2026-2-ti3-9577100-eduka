package br.pucminas.eduka.backend.dto.pessoas;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Alunos da escola inteira, para os cards e as abas da tela Alunos (IU07).
 *
 * @param porSituacao todas as situacoes, com zero quando nao ha aluno nela
 */
public record ResumoAlunosResponse(long total, Map<AlunoResponse.Situacao, Long> porSituacao) {

    public static ResumoAlunosResponse de(Collection<AlunoResponse> alunos) {
        Map<AlunoResponse.Situacao, Long> porSituacao = new EnumMap<>(AlunoResponse.Situacao.class);
        for (AlunoResponse.Situacao situacao : AlunoResponse.Situacao.values()) {
            porSituacao.put(situacao, 0L);
        }
        alunos.forEach(aluno -> porSituacao.merge(aluno.situacao(), 1L, Long::sum));
        return new ResumoAlunosResponse(alunos.size(), porSituacao);
    }
}
