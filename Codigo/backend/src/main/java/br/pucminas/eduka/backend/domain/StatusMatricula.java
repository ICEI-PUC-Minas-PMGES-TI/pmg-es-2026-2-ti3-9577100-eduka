package br.pucminas.eduka.backend.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Situacao da matricula de um aluno numa turma (coluna {@code STATUS}, V18).
 * So {@link #ATIVA} ocupa vaga da turma.
 *
 * <ul>
 * <li>ATIVA: cursando.</li>
 * <li>TRANCADA: afastado temporariamente; pode voltar a ATIVA.</li>
 * <li>CANCELADA: saiu da turma. Final.</li>
 * <li>CONCLUIDA: terminou a turma. Final.</li>
 * </ul>
 */
public enum StatusMatricula {
    ATIVA,
    TRANCADA,
    CANCELADA,
    CONCLUIDA;

    private static final Map<StatusMatricula, Set<StatusMatricula>> TRANSICOES = Map.of(
            ATIVA, EnumSet.of(TRANCADA, CANCELADA, CONCLUIDA),
            TRANCADA, EnumSet.of(ATIVA, CANCELADA),
            CANCELADA, EnumSet.noneOf(StatusMatricula.class),
            CONCLUIDA, EnumSet.noneOf(StatusMatricula.class));

    public boolean podeMudarPara(StatusMatricula novo) {
        return TRANSICOES.get(this).contains(novo);
    }
}
