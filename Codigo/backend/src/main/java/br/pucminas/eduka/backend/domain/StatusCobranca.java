package br.pucminas.eduka.backend.domain;

import java.time.LocalDate;

/**
 * Situacao de uma cobranca (coluna {@code STATUS}, V20).
 *
 * <ul>
 * <li>PENDENTE: aguardando pagamento, ainda no prazo.</li>
 * <li>EM_ATRASO: pendente com vencimento no passado. Nao e gravado: sai de
 * {@link #efetivo}.</li>
 * <li>PAGA: quitada. Final.</li>
 * <li>CANCELADA: anulada pela escola. Final.</li>
 * </ul>
 */
public enum StatusCobranca {
    PENDENTE,
    EM_ATRASO,
    PAGA,
    CANCELADA;

    /** So cobranca em aberto (pendente ou em atraso) e editada ou cancelada. */
    public boolean emAberto() {
        return this == PENDENTE || this == EM_ATRASO;
    }

    /** Situacao exibida: a gravada, trocando PENDENTE vencida por EM_ATRASO. */
    public static StatusCobranca efetivo(StatusCobranca gravado, LocalDate vencimento, LocalDate hoje) {
        return gravado == PENDENTE && vencimento.isBefore(hoje) ? EM_ATRASO : gravado;
    }
}
