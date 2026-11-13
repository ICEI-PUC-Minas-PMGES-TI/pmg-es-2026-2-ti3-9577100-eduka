package br.pucminas.eduka.backend.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Regras do codigo de recuperacao de senha (ver
 * {@link RecuperacaoSenhaService}).
 *
 * @param validadeMinutos          por quanto tempo cada codigo enviado vale
 * @param maxTentativas            codigos errados aceitos na janela, somando todos os envios
 * @param reenvioIntervaloSegundos espera minima entre dois envios
 * @param maxEnvios                codigos enviados na janela, contando o primeiro
 * @param janelaMinutos            duracao da janela em que valem os dois limites acima
 * @param codigoNoLog              escreve o codigo no log; so para dev sem Brevo
 */
@ConfigurationProperties(prefix = "app.senha.recuperacao")
public record RecuperacaoSenhaProperties(
        long validadeMinutos,
        int maxTentativas,
        long reenvioIntervaloSegundos,
        int maxEnvios,
        long janelaMinutos,
        boolean codigoNoLog) {
}
