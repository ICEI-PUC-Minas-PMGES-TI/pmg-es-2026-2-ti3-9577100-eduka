package br.pucminas.eduka.backend.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Garante que so um <em>access</em> token abre rota protegida.
 *
 * <p>
 * Sem isto, o resource server aceitaria qualquer JWT assinado com a chave —
 * inclusive o refresh token, que vive 30 dias e nao carrega perfil nem escola.
 * Um refresh vazado viraria credencial de longa duracao para a API inteira.
 * </p>
 */
final class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error TIPO_INVALIDO = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token nao e um access token",
            null);

    private static final OAuth2Error CLAIMS_AUSENTES = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Access token sem perfil ou escola",
            null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!JwtService.TOKEN_TYPE_ACCESS.equals(jwt.getClaimAsString(JwtService.CLAIM_TOKEN_TYPE))) {
            return OAuth2TokenValidatorResult.failure(TIPO_INVALIDO);
        }
        if (isBlank(jwt.getClaimAsString(JwtService.CLAIM_PERFIL))
                || isBlank(jwt.getClaimAsString(JwtService.CLAIM_ESCOLA_ID))) {
            return OAuth2TokenValidatorResult.failure(CLAIMS_AUSENTES);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}
