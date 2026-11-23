package br.pucminas.eduka.backend.domain.escola;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Modulo do Eduka que pode ser habilitado para uma escola.
 *
 * <p>
 * Os nomes precisam bater com o CHECK da coluna {@code ESCOLA_MODULO.MODULO}
 * (definido na V7).
 * </p>
 *
 * <p>
 * O que cada um libera esta em {@code RotasPorModulo}. Escola nova nasce com
 * todos; o SUPER_ADMIN tira os que nao foram contratados.
 * </p>
 */
public enum ModuloEscola {
    PORTAL_ALUNO,
    PORTAL_PROFESSOR,
    GESTAO_FINANCEIRA,
    SECRETARIA_VIRTUAL,
    BIBLIOTECA_DIGITAL,
    CONTROLE_FREQUENCIA;

    /** Converte os nomes gravados em {@code ESCOLA_MODULO}; nome desconhecido e ignorado. */
    public static Set<ModuloEscola> dosNomes(Collection<String> nomes) {
        Set<ModuloEscola> modulos = EnumSet.noneOf(ModuloEscola.class);
        for (String nome : nomes) {
            for (ModuloEscola modulo : values()) {
                if (modulo.name().equals(nome)) {
                    modulos.add(modulo);
                }
            }
        }
        return modulos;
    }
}
