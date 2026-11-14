package br.pucminas.eduka.backend.domain.escola;

/**
 * Modulo do Eduka que pode ser habilitado para uma escola.
 *
 * <p>
 * Os nomes precisam bater com o CHECK da coluna {@code ESCOLA_MODULO.MODULO}
 * (definido na V7).
 * </p>
 */
public enum ModuloEscola {
    PORTAL_ALUNO,
    PORTAL_PROFESSOR,
    GESTAO_FINANCEIRA,
    SECRETARIA_VIRTUAL,
    BIBLIOTECA_DIGITAL,
    CONTROLE_FREQUENCIA
}
