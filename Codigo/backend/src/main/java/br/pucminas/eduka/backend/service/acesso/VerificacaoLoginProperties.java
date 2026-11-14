package br.pucminas.eduka.backend.service.acesso;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Regras do codigo de verificacao do login (ver
 * {@link VerificacaoLoginService}).
 *
 * @param habilitada               desligada, o login volta a emitir os tokens so com a senha
 * @param validadeMinutos          por quanto tempo cada codigo enviado vale
 * @param maxTentativas            codigos errados aceitos antes de encerrar a verificacao
 * @param reenvioIntervaloSegundos espera minima entre dois envios
 * @param maxEnvios                codigos enviados por verificacao, contando o primeiro
 * @param codigoNoLog              escreve o codigo no log; so para dev sem Brevo
 */
@ConfigurationProperties(prefix = "app.login.verificacao")
public record VerificacaoLoginProperties(
        boolean habilitada,
        long validadeMinutos,
        int maxTentativas,
        long reenvioIntervaloSegundos,
        int maxEnvios,
        boolean codigoNoLog) {
}
