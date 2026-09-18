package br.pucminas.eduka.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class PerfilAuthenticationConverterTest {

    private final PerfilAuthenticationConverter converter = new PerfilAuthenticationConverter();

    @Test
    void perfilViraRoleEPrincipalViraUsuarioAutenticado() {
        UUID userId = UUID.randomUUID();
        UUID escolaId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("token_type", "access")
                .claim("perfil", "FINANCEIRO")
                .claim("escolaId", escolaId.toString())
                .build();

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FINANCEIRO");
        assertThat(authentication.getPrincipal())
                .isEqualTo(new UsuarioAutenticado(userId, "FINANCEIRO", escolaId));
        assertThat(authentication.getName()).isEqualTo(userId.toString());
    }

    @Test
    void superAdminEhReconhecidoPeloPerfil() {
        UsuarioAutenticado superAdmin = new UsuarioAutenticado(UUID.randomUUID(), "SUPER_ADMIN", UUID.randomUUID());
        UsuarioAutenticado master = new UsuarioAutenticado(UUID.randomUUID(), "MASTER", UUID.randomUUID());

        assertThat(superAdmin.isSuperAdmin()).isTrue();
        assertThat(master.isSuperAdmin()).isFalse();
    }
}
