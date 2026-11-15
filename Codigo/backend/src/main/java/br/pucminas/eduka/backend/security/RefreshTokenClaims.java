package br.pucminas.eduka.backend.security;

import java.time.Instant;
import java.util.UUID;

/** O que a renovacao da sessao precisa do refresh token: de quem e e quando saiu. */
public record RefreshTokenClaims(
        UUID userId,
        Instant issuedAt) {
}
