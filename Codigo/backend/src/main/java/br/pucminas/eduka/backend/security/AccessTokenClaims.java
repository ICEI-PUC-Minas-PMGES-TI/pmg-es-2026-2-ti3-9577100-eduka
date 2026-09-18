package br.pucminas.eduka.backend.security;

import java.time.Instant;
import java.util.UUID;

public record AccessTokenClaims(
        UUID userId,
        String perfil,
        UUID escolaId,
        Instant expiresAt) {
}
