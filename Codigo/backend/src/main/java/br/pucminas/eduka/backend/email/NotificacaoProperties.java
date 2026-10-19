package br.pucminas.eduka.backend.email;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Regras da fila de notificacoes ({@code app.notificacao.*}).
 *
 * @param maxTentativas quantos envios uma notificacao tem ao todo (o
 *                      imediato conta) antes de virar FALHOU
 * @param esperaInicial espera antes da segunda tentativa; cada nova falha
 *                      multiplica por 4
 * @param esperaMaxima  teto da espera entre tentativas
 * @param lote          quantas notificacoes a rotina processa por execucao
 */
@ConfigurationProperties("app.notificacao")
public record NotificacaoProperties(
        int maxTentativas,
        Duration esperaInicial,
        Duration esperaMaxima,
        int lote) {

    /** Espera depois da {@code tentativas}-esima falha: inicial, x4, x16... ate o teto. */
    public Duration esperaApos(int tentativas) {
        Duration espera = esperaInicial;
        for (int i = 1; i < tentativas && espera.compareTo(esperaMaxima) < 0; i++) {
            espera = espera.multipliedBy(4);
        }
        return espera.compareTo(esperaMaxima) > 0 ? esperaMaxima : espera;
    }
}
