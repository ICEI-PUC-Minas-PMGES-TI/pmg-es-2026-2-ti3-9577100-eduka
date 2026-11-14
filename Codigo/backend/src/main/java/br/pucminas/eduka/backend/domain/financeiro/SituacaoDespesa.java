package br.pucminas.eduka.backend.domain.financeiro;

/**
 * Situacao de uma despesa (coluna {@code DESPESA.SITUACAO}, V31).
 *
 * <ul>
 * <li>A_PAGAR: lancada e ainda nao paga.</li>
 * <li>PAGA: quitada. Volta para A_PAGAR se foi marcada por engano.</li>
 * <li>CANCELADA: anulada pela escola. Final.</li>
 * </ul>
 *
 * <p>
 * Mudancas permitidas ({@link #podeMudarPara}):
 * </p>
 * <ul>
 * <li>A_PAGAR para PAGA ou CANCELADA.</li>
 * <li>PAGA para A_PAGAR (desfaz o pagamento).</li>
 * <li>CANCELADA nao muda mais.</li>
 * </ul>
 */
public enum SituacaoDespesa {
    A_PAGAR,
    PAGA,
    CANCELADA;

    /** Se a despesa nesta situacao pode passar para {@code nova}. Ficar onde esta nao e mudanca. */
    public boolean podeMudarPara(SituacaoDespesa nova) {
        return switch (this) {
            case A_PAGAR -> nova == PAGA || nova == CANCELADA;
            case PAGA -> nova == A_PAGAR;
            case CANCELADA -> false;
        };
    }
}
