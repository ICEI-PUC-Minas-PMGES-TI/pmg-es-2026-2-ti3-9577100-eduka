package br.pucminas.eduka.backend.exception.escola;

import br.pucminas.eduka.backend.domain.escola.ModuloEscola;

/**
 * A escola nao contratou o modulo que a rota ou o perfil exige (ver
 * {@code RotasPorModulo}). Sai no login, para o perfil que so existe com o
 * modulo, e de qualquer requisicao ja logada ({@code ModuloContratadoFilter}).
 */
public class ModuloNaoContratadoException extends RuntimeException {

    private final transient ModuloEscola modulo;

    public ModuloNaoContratadoException(ModuloEscola modulo) {
        super("O modulo %s nao esta contratado pela escola. Fale com a administracao da escola"
                .formatted(modulo.name()));
        this.modulo = modulo;
    }

    public ModuloEscola getModulo() {
        return modulo;
    }
}
