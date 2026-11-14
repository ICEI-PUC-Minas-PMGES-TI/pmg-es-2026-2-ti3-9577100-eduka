package br.pucminas.eduka.backend.domain.acesso;

/**
 * Perfis de acesso previstos no documento de visao (RF04).
 *
 * <p>
 * Os nomes precisam bater com o CHECK da coluna {@code USUARIO.PERFIL}
 * (definido na V1).
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
