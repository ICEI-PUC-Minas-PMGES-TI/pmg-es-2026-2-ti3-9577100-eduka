package br.pucminas.eduka.backend.security;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.PerfilUsuario;

/**
 * Quem esta fazendo a requisicao, extraido do access token. E o principal do
 * {@code SecurityContext} em toda rota protegida.
 */
public record UsuarioAutenticado(
        UUID id,
        String perfil,
        UUID escolaId) {

    public boolean isSuperAdmin() {
        return PerfilUsuario.SUPER_ADMIN.name().equals(perfil);
    }

    public PerfilUsuario perfilUsuario() {
        return PerfilUsuario.valueOf(perfil);
    }
}
