package br.pucminas.eduka.backend.domain.financeiro;

/**
 * Nivel do risco de atraso de um aluno, pela soma dos pontos das regras de
 * {@link br.pucminas.eduka.backend.service.financeiro.RegrasRiscoAtraso}.
 *
 * <ul>
 * <li>BAIXO: 0 ou 1 ponto.</li>
 * <li>MEDIO: 2 ou 3 pontos.</li>
 * <li>ALTO: 4 pontos ou mais.</li>
 * </ul>
 */
public enum NivelRiscoAtraso {
    BAIXO,
    MEDIO,
    ALTO;

    /** Pontos minimos para o nivel MEDIO. */
    public static final int PONTOS_MEDIO = 2;

    /** Pontos minimos para o nivel ALTO. */
    public static final int PONTOS_ALTO = 4;

    public static NivelRiscoAtraso dePontos(int pontos) {
        if (pontos >= PONTOS_ALTO) {
            return ALTO;
        }
        return pontos >= PONTOS_MEDIO ? MEDIO : BAIXO;
    }
}
