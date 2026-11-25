package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Como o aluno esta com a escola no dinheiro, para a lista de alunos (IU07)
 * do MASTER e do FINANCEIRO. Conta so as cobrancas nao canceladas, com a
 * situacao efetiva de cada uma ({@code StatusCobranca#efetivo}): a pendente
 * vencida ja conta como em atraso.
 *
 * @param situacao          uma so por aluno, na ordem de {@link Situacao}
 * @param valorEmAberto     soma das cobrancas pendentes e em atraso
 * @param valorEmAtraso     so as em atraso
 * @param diasDeAtraso      dias desde o vencimento da cobranca em atraso mais
 *                          antiga; nulo sem atraso
 * @param faixaAtraso       a faixa de {@code diasDeAtraso}; nula sem atraso
 * @param proximoVencimento a pendente que vence primeiro, de hoje em diante;
 *                          nulo sem pendente
 * @param venceEm7Dias      se a proxima pendente vence de hoje ate daqui a
 *                          {@link #DIAS_A_VENCER} dias
 * @param valorVenceEm7Dias soma das pendentes que vencem nesse intervalo
 * @param mensalidadeNoMes  se ha mensalidade com vencimento no mes atual
 */
public record FinanceiroDoAlunoResponse(
        Situacao situacao,
        BigDecimal valorEmAberto,
        BigDecimal valorEmAtraso,
        Integer diasDeAtraso,
        FaixaAtraso faixaAtraso,
        LocalDate proximoVencimento,
        boolean venceEm7Dias,
        BigDecimal valorVenceEm7Dias,
        boolean mensalidadeNoMes) {

    /** Quantos dias a frente {@code venceEm7Dias} olha, a partir de hoje. */
    public static final int DIAS_A_VENCER = 7;

    /**
     * Situacao financeira do aluno. Vale a primeira que se aplica: quem tem
     * cobranca em atraso esta EM_ATRASO mesmo tendo outra a vencer.
     */
    public enum Situacao {
        /** Ao menos uma cobranca em atraso. */
        EM_ATRASO,
        /** Cobranca pendente no prazo e nenhuma em atraso. */
        A_VENCER,
        /** Tem cobrancas, todas pagas. */
        EM_DIA,
        /** Nenhuma cobranca (as canceladas nao contam). */
        SEM_COBRANCA
    }

    /** Ha quanto tempo venceu a cobranca em atraso mais antiga do aluno. */
    public enum FaixaAtraso {
        ATE_30_DIAS, DE_31_A_60_DIAS, MAIS_DE_60_DIAS;

        public static FaixaAtraso de(int dias) {
            if (dias <= 30) {
                return ATE_30_DIAS;
            }
            return dias <= 60 ? DE_31_A_60_DIAS : MAIS_DE_60_DIAS;
        }
    }

    /** O aluno sem nenhuma cobranca nao cancelada. */
    public static final FinanceiroDoAlunoResponse SEM_COBRANCA = new FinanceiroDoAlunoResponse(Situacao.SEM_COBRANCA,
            BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, false, BigDecimal.ZERO, false);

    /**
     * Monta a situacao a partir dos totais das cobrancas do aluno.
     *
     * @param emAtraso                     quantas cobrancas em atraso
     * @param aVencer                      quantas pendentes no prazo
     * @param vencimentoMaisAntigoEmAtraso nulo sem atraso
     * @param proximoVencimento            a pendente de hoje em diante que
     *                                     vence primeiro; nulo sem pendente
     * @param hoje                         o dia na escola
     */
    public static FinanceiroDoAlunoResponse de(long emAtraso, long aVencer, BigDecimal valorEmAberto,
            BigDecimal valorEmAtraso, LocalDate vencimentoMaisAntigoEmAtraso, LocalDate proximoVencimento,
            BigDecimal valorVenceEm7Dias, boolean mensalidadeNoMes, LocalDate hoje) {
        Situacao situacao = emAtraso > 0 ? Situacao.EM_ATRASO : aVencer > 0 ? Situacao.A_VENCER : Situacao.EM_DIA;
        Integer dias = emAtraso > 0 && vencimentoMaisAntigoEmAtraso != null
                ? (int) Math.max(0, ChronoUnit.DAYS.between(vencimentoMaisAntigoEmAtraso, hoje))
                : null;
        boolean venceEm7Dias = proximoVencimento != null
                && !proximoVencimento.isAfter(hoje.plusDays(DIAS_A_VENCER));
        return new FinanceiroDoAlunoResponse(situacao, zeroSeNulo(valorEmAberto), zeroSeNulo(valorEmAtraso), dias,
                dias == null ? null : FaixaAtraso.de(dias), proximoVencimento, venceEm7Dias,
                zeroSeNulo(valorVenceEm7Dias), mensalidadeNoMes);
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
