package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.PerfilUsuario;

/**
 * Com {@code trocaSenhaObrigatoria} verdadeiro os tokens vem nulos: o usuario
 * entrou com a senha provisoria e precisa definir a definitiva em
 * {@code POST /primeiro-acesso} antes de usar o sistema.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        UUID usuarioId,
        PerfilUsuario perfil,
        boolean trocaSenhaObrigatoria) {

    public static LoginResponse autenticado(String accessToken, String refreshToken, UUID usuarioId,
            PerfilUsuario perfil) {
        return new LoginResponse(accessToken, refreshToken, usuarioId, perfil, false);
    }

    public static LoginResponse trocaDeSenhaPendente(UUID usuarioId, PerfilUsuario perfil) {
        return new LoginResponse(null, null, usuarioId, perfil, true);
    }
}
