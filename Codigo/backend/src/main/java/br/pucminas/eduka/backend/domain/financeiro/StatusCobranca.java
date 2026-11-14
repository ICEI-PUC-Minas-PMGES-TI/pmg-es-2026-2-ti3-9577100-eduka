package br.pucminas.eduka.backend.domain.financeiro;

import java.time.LocalDate;

/**
 * Situacao de uma cobranca (coluna {@code STATUS}, V20 e V21).
 *
 * <ul>
 * <li>PENDENTE: aguardando pagamento, ainda no prazo.</li>
 * <li>EM_ATRASO: aguardando pagamento, com o vencimento no passado.</li>
 * <li>PAGA: quitada. Final.</li>
 * <li>CANCELADA: anulada pela escola. Final.</li>
 * </ul>
 *
 * <p>
 * Mudancas permitidas ({@link #podeMudarPara}):
 * </p>
 * <ul>
 * <li>PENDENTE para EM_ATRASO (venceu), PAGA ou CANCELADA.</li>
 * <li>EM_ATRASO para PENDENTE (vencimento adiado), PAGA ou CANCELADA.</li>
 * <li>PAGA e CANCELADA nao mudam mais.</li>
 * </ul>
 */
public enum StatusCobranca {
    PENDENTE,
    EM_ATRASO,
    PAGA,
    CANCELADA;

    /** So cobranca em aberto (pendente ou em atraso) e editada, paga ou cancelada. */
    public boolean emAberto() {
        return this == PENDENTE || this == EM_ATRASO;
    }

    /** Se a cobranca nesta situacao pode passar para {@code novo}. Ficar onde esta nao e mudanca. */
    public boolean podeMudarPara(StatusCobranca novo) {
        return emAberto() && novo != this;
    }

    /** Situacao da cobranca em aberto com esse vencimento: vence no proprio dia, atrasa no seguinte. */
    public static StatusCobranca emAbertoCom(LocalDate vencimento, LocalDate hoje) {
        return vencimento.isBefore(hoje) ? EM_ATRASO : PENDENTE;
    }

    /**
     * Situacao exibida: a gravada, trocando PENDENTE vencida por EM_ATRASO. Cobre
     * o intervalo entre a virada do dia e a rotina que grava o atraso.
     */
    public static StatusCobranca efetivo(StatusCobranca gravado, LocalDate vencimento, LocalDate hoje) {
        return gravado == PENDENTE ? emAbertoCom(vencimento, hoje) : gravado;
    }
}
