package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * A situacao financeira dos alunos da escola inteira, para os cards e as
 * pilulas financeiras da tela Alunos (IU07). Como o resto do resumo, nao
 * muda com a busca nem com os filtros.
 *
 * @param porSituacao       todas as situacoes, com zero quando nao ha aluno
 * @param porFaixaAtraso    os alunos em atraso, pela faixa; todas as faixas
 * @param valorEmAtraso     soma do em atraso de todos os alunos
 * @param venceEm7Dias      alunos com pendente vencendo nos proximos 7 dias
 * @param valorVenceEm7Dias soma dessas pendentes
 * @param semMensalidadeNoMes alunos sem mensalidade com vencimento no mes
 */
public record ResumoFinanceiroAlunosResponse(
        Map<FinanceiroDoAlunoResponse.Situacao, Long> porSituacao,
        Map<FinanceiroDoAlunoResponse.FaixaAtraso, Long> porFaixaAtraso,
        BigDecimal valorEmAtraso,
        long venceEm7Dias,
        BigDecimal valorVenceEm7Dias,
        long semMensalidadeNoMes) {

    public static ResumoFinanceiroAlunosResponse de(Collection<FinanceiroDoAlunoResponse> alunos) {
        Map<FinanceiroDoAlunoResponse.Situacao, Long> porSituacao = zerado(FinanceiroDoAlunoResponse.Situacao.class);
        Map<FinanceiroDoAlunoResponse.FaixaAtraso, Long> porFaixa = zerado(FinanceiroDoAlunoResponse.FaixaAtraso.class);
        BigDecimal valorEmAtraso = BigDecimal.ZERO;
        BigDecimal valorVenceEm7Dias = BigDecimal.ZERO;
        long venceEm7Dias = 0;
        long semMensalidade = 0;
        for (FinanceiroDoAlunoResponse aluno : alunos) {
            porSituacao.merge(aluno.situacao(), 1L, Long::sum);
            if (aluno.faixaAtraso() != null) {
                porFaixa.merge(aluno.faixaAtraso(), 1L, Long::sum);
            }
            valorEmAtraso = valorEmAtraso.add(aluno.valorEmAtraso());
            if (aluno.venceEm7Dias()) {
                venceEm7Dias++;
                valorVenceEm7Dias = valorVenceEm7Dias.add(aluno.valorVenceEm7Dias());
            }
            if (!aluno.mensalidadeNoMes()) {
                semMensalidade++;
            }
        }
        return new ResumoFinanceiroAlunosResponse(porSituacao, porFaixa, valorEmAtraso, venceEm7Dias,
                valorVenceEm7Dias, semMensalidade);
    }

    private static <E extends Enum<E>> Map<E, Long> zerado(Class<E> tipo) {
        Map<E, Long> mapa = new EnumMap<>(tipo);
        for (E valor : tipo.getEnumConstants()) {
            mapa.put(valor, 0L);
        }
        return mapa;
    }
}
