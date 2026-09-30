package br.pucminas.eduka.backend.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado por {@link AcessoService} quando um acesso inicial e criado ou
 * reenviado. Nao carrega o CPF: o e-mail so diz que a senha e o CPF, nunca o
 * mostra.
 */
public record AcessoInicialCriado(
        UUID usuarioId,
        String nome,
        String email,
        String login,
        Instant expiraEm) {

    @Override
    public String toString() {
        return "AcessoInicialCriado[usuarioId=%s]".formatted(usuarioId);
    }
}
