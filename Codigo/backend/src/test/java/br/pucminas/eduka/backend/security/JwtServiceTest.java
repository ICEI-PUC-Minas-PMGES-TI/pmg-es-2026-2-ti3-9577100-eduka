package br.pucminas.eduka.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.ChronoField;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes!!";

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private JwtProperties jwtProperties;
    private Clock clock;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtProperties = new JwtProperties(SECRET, 60, 30);
        clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        jwtService = new JwtService(jwtEncoder, jwtDecoder, jwtProperties, clock);
    }

    @Test
    void generatesAndVerifiesAccessToken() {
        UUID userId = UUID.randomUUID();
        UUID escolaId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "PROFESSOR", escolaId);
        AccessTokenClaims claims = jwtService.verifyAccessToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.perfil()).isEqualTo("PROFESSOR");
        assertThat(claims.escolaId()).isEqualTo(escolaId);
        // JWT tokens preserve only second precision for exp claim
        Instant expectedExpiry = clock.instant().plus(60, ChronoUnit.MINUTES)
                .with(ChronoField.NANO_OF_SECOND, 0);
        assertThat(claims.expiresAt()).isEqualTo(expectedExpiry);
    }

    @Test
    void generatesAndVerifiesAccessTokenForSuperAdminSemEscola() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "SUPER_ADMIN", null);
        AccessTokenClaims claims = jwtService.verifyAccessToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.perfil()).isEqualTo("SUPER_ADMIN");
        assertThat(claims.escolaId()).isNull();
    }

    @Test
    void generatesAndVerifiesRefreshToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(userId);
        RefreshTokenClaims claims = jwtService.verifyRefreshToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        // A renovacao compara a emissao com a ultima troca de senha.
        assertThat(claims.issuedAt()).isEqualTo(clock.instant().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void rejectsTamperedAccessToken() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "ALUNO", UUID.randomUUID());
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "."
                + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);

        assertThatThrownBy(() -> jwtService.verifyAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedRefreshToken() {
        String token = jwtService.generateRefreshToken(UUID.randomUUID());
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "."
                + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);

        assertThatThrownBy(() -> jwtService.verifyRefreshToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredAccessToken() {
        Clock pastClock = Clock.fixed(clock.instant().minus(2, ChronoUnit.HOURS), ZoneOffset.UTC);
        JwtService expiredTokenIssuer = new JwtService(jwtEncoder, jwtDecoder, jwtProperties, pastClock);
        String expiredToken = expiredTokenIssuer.generateAccessToken(
                UUID.randomUUID(), "ALUNO", UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.verifyAccessToken(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAccessTokenPassedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(UUID.randomUUID(), "ALUNO", UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.verifyRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsRefreshTokenPassedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.verifyAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }
}
