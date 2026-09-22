package br.pucminas.eduka.backend.security;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Transforma o JWT validado no {@link org.springframework.security.core.Authentication}
 * que o Spring Security consulta.
 *
 * <p>
 * A claim {@code perfil} vira a authority {@code ROLE_<perfil>}, o que faz
 * {@code @PreAuthorize("hasRole('SECRETARIA')")} funcionar em qualquer
 * controller. O principal e um {@link UsuarioAutenticado}, para que servicos
 * leiam id, perfil e escola sem mexer em claims.
 * </p>
 */
final class PerfilAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String PREFIXO_ROLE = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String perfil = jwt.getClaimAsString(JwtService.CLAIM_PERFIL);
        String escolaId = jwt.getClaimAsString(JwtService.CLAIM_ESCOLA_ID);
        UsuarioAutenticado usuario = new UsuarioAutenticado(
                UUID.fromString(jwt.getSubject()),
                perfil,
                escolaId != null ? UUID.fromString(escolaId) : null);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(PREFIXO_ROLE + perfil));

        return new UsuarioAutenticadoToken(jwt, authorities, usuario);
    }

    /**
     * {@link JwtAuthenticationToken} cujo principal e o {@link UsuarioAutenticado}
     * em vez do {@link Jwt} cru.
     */
    static final class UsuarioAutenticadoToken extends JwtAuthenticationToken {

        private final UsuarioAutenticado usuario;

        UsuarioAutenticadoToken(Jwt jwt, List<GrantedAuthority> authorities, UsuarioAutenticado usuario) {
            super(jwt, authorities, usuario.id().toString());
            this.usuario = usuario;
        }

        @Override
        public Object getPrincipal() {
            return usuario;
        }
    }
}
