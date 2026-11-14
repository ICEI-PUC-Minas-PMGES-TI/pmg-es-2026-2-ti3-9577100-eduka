package br.pucminas.eduka.backend.dto.acesso;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;

/**
 * Os tokens vem nulos enquanto o login nao terminou:
 * <ul>
 * <li>{@code trocaSenhaObrigatoria} verdadeiro: o usuario entrou com a senha
 * provisoria e precisa definir a definitiva em {@code POST /primeiro-acesso};</li>
 * <li>{@code verificacao} preenchida: falta confirmar em
 * {@code POST /login/codigo} o codigo enviado por e-mail.</li>
 * </ul>
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        UUID usuarioId,
        PerfilUsuario perfil,
        boolean trocaSenhaObrigatoria,
        VerificacaoLoginResponse verificacao) {

    public static LoginResponse autenticado(String accessToken, String refreshToken, UUID usuarioId,
            PerfilUsuario perfil) {
        return new LoginResponse(accessToken, refreshToken, usuarioId, perfil, false, null);
    }

    public static LoginResponse trocaDeSenhaPendente(UUID usuarioId, PerfilUsuario perfil) {
        return new LoginResponse(null, null, usuarioId, perfil, true, null);
    }

    public static LoginResponse verificacaoPendente(UUID usuarioId, PerfilUsuario perfil,
            VerificacaoLoginResponse verificacao) {
        return new LoginResponse(null, null, usuarioId, perfil, false, verificacao);
    }
}
