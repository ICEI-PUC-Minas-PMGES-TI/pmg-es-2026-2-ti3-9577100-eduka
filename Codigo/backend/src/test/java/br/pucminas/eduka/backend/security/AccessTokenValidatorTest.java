package br.pucminas.eduka.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AccessTokenValidatorTest {

    private final AccessTokenValidator validator = new AccessTokenValidator();

    private static Jwt jwt(UnaryOperator<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        return claims.apply(builder).build();
    }

    private static Jwt accessCompleto() {
        return jwt(b -> b
                .claim("token_type", "access")
                .claim("perfil", "SECRETARIA")
                .claim("escolaId", UUID.randomUUID().toString()));
    }

    @Test
    void aceitaAccessTokenComPerfilEEscola() {
        assertThat(validator.validate(accessCompleto()).hasErrors()).isFalse();
    }

    @Test
    void rejeitaRefreshToken() {
        Jwt refresh = jwt(b -> b.claim("token_type", "refresh"));

        assertThat(validator.validate(refresh).hasErrors()).isTrue();
    }

    @Test
    void rejeitaTokenSemTipo() {
        Jwt semTipo = jwt(b -> b
                .claim("perfil", "SECRETARIA")
                .claim("escolaId", UUID.randomUUID().toString()));

        assertThat(validator.validate(semTipo).hasErrors()).isTrue();
    }

    @Test
    void rejeitaAccessTokenSemPerfil() {
        Jwt semPerfil = jwt(b -> b
                .claim("token_type", "access")
                .claim("escolaId", UUID.randomUUID().toString()));

        assertThat(validator.validate(semPerfil).hasErrors()).isTrue();
    }

    @Test
    void aceitaAccessTokenSemEscola() {
        // Usuarios sem escola (hoje, so SUPER_ADMIN) tem token legitimo assim.
        Jwt semEscola = jwt(b -> b
                .claim("token_type", "access")
                .claim("perfil", "SUPER_ADMIN"));

        assertThat(validator.validate(semEscola).hasErrors()).isFalse();
    }
}
