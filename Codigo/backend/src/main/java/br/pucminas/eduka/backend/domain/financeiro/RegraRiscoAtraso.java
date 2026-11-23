package br.pucminas.eduka.backend.domain.financeiro;

/**
 * Regras que somam pontos no risco de atraso de um aluno. Cada uma vale de 0
 * a 2 pontos; os limites estao em
 * {@link br.pucminas.eduka.backend.service.financeiro.RegrasRiscoAtraso}.
 */
public enum RegraRiscoAtraso {
    /** Quantas cobrancas atrasaram na janela: pagas depois do vencimento ou vencidas em aberto. */
    ATRASOS_ANTERIORES,
    /** Media de dias de atraso dessas cobrancas. */
    DIAS_MEDIOS_ATRASO,
    /** Valor em aberto ja vencido, comparado com o valor medio das cobrancas do aluno. */
    VALOR_EM_ABERTO
}
