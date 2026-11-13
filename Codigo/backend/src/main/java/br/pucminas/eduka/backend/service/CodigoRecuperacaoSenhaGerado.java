package br.pucminas.eduka.backend.service;

import java.util.UUID;

/**
 * Publicado por {@link RecuperacaoSenhaService} a cada codigo gerado. E o
 * unico lugar em que o codigo existe em texto puro depois de gerado; por isso
 * fica fora do {@code toString}.
 */
public record CodigoRecuperacaoSenhaGerado(
        UUID usuarioId,
        String nome,
        String email,
        String codigo) {

    @Override
    public String toString() {
        return "CodigoRecuperacaoSenhaGerado[usuarioId=%s]".formatted(usuarioId);
    }
}
