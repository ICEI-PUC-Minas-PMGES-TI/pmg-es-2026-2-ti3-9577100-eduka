package br.pucminas.eduka.backend.dto.acesso;

import java.util.UUID;

/**
 * Codigo de verificacao enviado e ainda nao confirmado. Os prazos vao em
 * segundos a partir de agora, e nao como data: o cronometro do front nao
 * depende de o relogio do aparelho estar certo.
 */
public record VerificacaoLoginResponse(
        UUID id,
        long validadeSegundos,
        long reenvioSegundos) {
}
