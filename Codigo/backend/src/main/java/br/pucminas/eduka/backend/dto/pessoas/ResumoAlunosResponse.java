package br.pucminas.eduka.backend.dto.pessoas;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import br.pucminas.eduka.backend.dto.financeiro.ResumoFinanceiroAlunosResponse;

/**
 * Alunos da escola inteira, para os cards e as abas da tela Alunos (IU07).
 *
 * @param porSituacao todas as situacoes, com zero quando nao ha aluno nela
 * @param financeiro  a situacao financeira dos alunos; nulo para quem nao ve
 *                    o financeiro (a SECRETARIA, ou a escola sem a gestao
 *                    financeira)
 */
public record ResumoAlunosResponse(long total, Map<AlunoResponse.Situacao, Long> porSituacao,
        ResumoFinanceiroAlunosResponse financeiro) {

    public static ResumoAlunosResponse de(Collection<AlunoResponse> alunos) {
        Map<AlunoResponse.Situacao, Long> porSituacao = new EnumMap<>(AlunoResponse.Situacao.class);
        for (AlunoResponse.Situacao situacao : AlunoResponse.Situacao.values()) {
            porSituacao.put(situacao, 0L);
        }
        alunos.forEach(aluno -> porSituacao.merge(aluno.situacao(), 1L, Long::sum));
        return new ResumoAlunosResponse(alunos.size(), porSituacao, null);
    }

    /** O mesmo resumo com a situacao financeira. */
    public ResumoAlunosResponse comFinanceiro(ResumoFinanceiroAlunosResponse resumoFinanceiro) {
        return new ResumoAlunosResponse(total, porSituacao, resumoFinanceiro);
    }
}
