package br.pucminas.eduka.backend.domain.escola;

/**
 * Plano contratado pela escola.
 *
 * <p>
 * Os nomes precisam bater com o CHECK da coluna {@code ESCOLA.PLANO}
 * (definido na V6).
 * </p>
 */
public enum PlanoEscola {
    BASICO,
    AVANCADO,
    ENTERPRISE
}
