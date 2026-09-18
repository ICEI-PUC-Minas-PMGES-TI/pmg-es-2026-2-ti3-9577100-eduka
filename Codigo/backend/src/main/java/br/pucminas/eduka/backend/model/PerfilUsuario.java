package br.pucminas.eduka.backend.model;

/**
 * Perfis de acesso previstos no documento de visao (RF04).
 *
 * <p>
 * Os nomes precisam bater com o CHECK da coluna {@code usuarios.perfil}
 * (definido na V1, renomeado na V3).
 * </p>
 */
public enum PerfilUsuario {
    SUPER_ADMIN,
    MASTER,
    SECRETARIA,
    FINANCEIRO,
    PROFESSOR,
    ALUNO
}
