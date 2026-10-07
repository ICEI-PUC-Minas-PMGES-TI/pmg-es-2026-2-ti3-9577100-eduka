package br.pucminas.eduka.backend.service;

import java.util.UUID;

/**
 * Publicado por {@link VerificacaoLoginService} a cada codigo gerado (primeiro
 * envio ou reenvio). E o unico lugar em que o codigo existe em texto puro
 * depois de gerado; por isso fica fora do {@code toString}.
 */
public record CodigoLoginGerado(
        UUID usuarioId,
        String nome,
        String email,
        String codigo) {

    @Override
    public String toString() {
        return "CodigoLoginGerado[usuarioId=%s]".formatted(usuarioId);
    }
}
