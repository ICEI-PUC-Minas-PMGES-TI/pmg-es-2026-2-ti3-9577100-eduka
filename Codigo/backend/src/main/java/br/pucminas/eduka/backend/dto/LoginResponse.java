package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.model.PerfilUsuario;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        UUID usuarioId,
        PerfilUsuario perfil) {
}
