package br.pucminas.eduka.backend.dto.comum;

/**
 * Corpo do {@code GET /health}. So estados (UP ou DOWN), nunca mensagem de
 * excecao nem dado do banco: a rota e publica.
 */
public record HealthResponse(String status, String banco) {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";

    public static HealthResponse de(boolean bancoNoAr) {
        String estado = bancoNoAr ? UP : DOWN;
        return new HealthResponse(estado, estado);
    }

    public boolean noAr() {
        return UP.equals(status);
    }
}
