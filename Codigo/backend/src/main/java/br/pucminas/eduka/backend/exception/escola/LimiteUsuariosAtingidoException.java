package br.pucminas.eduka.backend.exception.escola;

/**
 * A escola ja tem tantos logins ativos quanto o limite do plano (ver
 * {@code LimiteUsuariosService}): nenhum login novo nem reativado ate alguem
 * ser inativado ou o SUPER_ADMIN ampliar o limite.
 */
public class LimiteUsuariosAtingidoException extends RuntimeException {

    private final int limite;

    public LimiteUsuariosAtingidoException(int limite) {
        super("A escola atingiu o limite de %d usuarios ativos do plano. Inative um acesso ou fale com o suporte do Eduka para ampliar o limite"
                .formatted(limite));
        this.limite = limite;
    }

    public int getLimite() {
        return limite;
    }
}
