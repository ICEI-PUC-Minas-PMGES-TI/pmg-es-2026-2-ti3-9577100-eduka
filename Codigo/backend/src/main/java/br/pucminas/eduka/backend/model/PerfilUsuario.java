package br.pucminas.eduka.backend.model;

/**
 * Perfis de acesso previstos no documento de visao (RF04).
 *
 * <p>
 * Os nomes precisam bater com o CHECK da coluna {@code USUARIO.PERFIL} definido
 * na migration V1.
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
