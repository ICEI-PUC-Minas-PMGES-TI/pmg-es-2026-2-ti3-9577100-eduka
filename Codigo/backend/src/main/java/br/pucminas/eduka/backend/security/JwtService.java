package br.pucminas.eduka.backend.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    // Package-private: AccessTokenValidator e PerfilAuthenticationConverter leem
    // as mesmas claims que este servico escreve.
    static final String CLAIM_TOKEN_TYPE = "token_type";
    static final String CLAIM_PERFIL = "perfil";
    static final String CLAIM_ESCOLA_ID = "escolaId";
    static final String TOKEN_TYPE_ACCESS = "access";
    static final String TOKEN_TYPE_REFRESH = "refresh";

    private static final JwsHeader HS256_HEADER = JwsHeader.with(MacAlgorithm.HS256).build();

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder,
            JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /**
     * {@code escolaId} nulo (hoje, so {@code SUPER_ADMIN}) simplesmente nao
     * entra no token: nao ha "escola vazia" que faca sentido carregar.
     */
    public String generateAccessToken(UUID userId, String perfil, UUID escolaId) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessExpirationMinutes(), ChronoUnit.MINUTES))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_PERFIL, perfil);
        if (escolaId != null) {
            claimsBuilder.claim(CLAIM_ESCOLA_ID, escolaId.toString());
        }
        JwtClaimsSet claims = claimsBuilder.build();
        return jwtEncoder.encode(JwtEncoderParameters.from(HS256_HEADER, claims)).getTokenValue();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.refreshExpirationDays(), ChronoUnit.DAYS))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(HS256_HEADER, claims)).getTokenValue();
    }

    public AccessTokenClaims verifyAccessToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        requireTokenType(jwt, TOKEN_TYPE_ACCESS);
        String escolaId = jwt.getClaimAsString(CLAIM_ESCOLA_ID);
        return new AccessTokenClaims(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(CLAIM_PERFIL),
                escolaId != null ? UUID.fromString(escolaId) : null,
                jwt.getExpiresAt());
    }

    public UUID verifyRefreshToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        requireTokenType(jwt, TOKEN_TYPE_REFRESH);
        return UUID.fromString(jwt.getSubject());
    }

    private void requireTokenType(Jwt jwt, String expectedType) {
        String actualType = jwt.getClaimAsString(CLAIM_TOKEN_TYPE);
        if (!expectedType.equals(actualType)) {
            throw new JwtException("Token type inesperado: esperado '%s', encontrado '%s'"
                    .formatted(expectedType, actualType));
        }
    }
}
