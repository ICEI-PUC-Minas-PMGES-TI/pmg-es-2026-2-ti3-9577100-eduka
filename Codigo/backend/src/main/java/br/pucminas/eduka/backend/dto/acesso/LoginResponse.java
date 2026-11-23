package br.pucminas.eduka.backend.dto.acesso;

import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.escola.ModuloEscola;

/**
 * Os tokens vem nulos enquanto o login nao terminou:
 * <ul>
 * <li>{@code trocaSenhaObrigatoria} verdadeiro: o usuario entrou com a senha
 * provisoria e precisa definir a definitiva em {@code POST /primeiro-acesso};</li>
 * <li>{@code verificacao} preenchida: falta confirmar em
 * {@code POST /login/codigo} o codigo enviado por e-mail.</li>
 * </ul>
 *
 * <p>
 * {@code modulos} sao os contratados pela escola (ver {@code RotasPorModulo}),
 * para a tela esconder o que a escola nao tem. Nulo sem sessao aberta e para
 * o SUPER_ADMIN, que nao tem escola.
 * </p>
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        UUID usuarioId,
        PerfilUsuario perfil,
        boolean trocaSenhaObrigatoria,
        VerificacaoLoginResponse verificacao,
        List<ModuloEscola> modulos) {

    public static LoginResponse autenticado(String accessToken, String refreshToken, UUID usuarioId,
            PerfilUsuario perfil, List<ModuloEscola> modulos) {
        return new LoginResponse(accessToken, refreshToken, usuarioId, perfil, false, null, modulos);
    }

    public static LoginResponse trocaDeSenhaPendente(UUID usuarioId, PerfilUsuario perfil) {
        return new LoginResponse(null, null, usuarioId, perfil, true, null, null);
    }

    public static LoginResponse verificacaoPendente(UUID usuarioId, PerfilUsuario perfil,
            VerificacaoLoginResponse verificacao) {
        return new LoginResponse(null, null, usuarioId, perfil, false, verificacao, null);
    }
}
