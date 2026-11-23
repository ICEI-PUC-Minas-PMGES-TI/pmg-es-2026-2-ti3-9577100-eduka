package br.pucminas.eduka.backend.domain.pessoas;

/**
 * Vinculo empregaticio do professor com a escola.
 *
 * <p>
 * Os nomes precisam bater com o CHECK da coluna {@code PROFESSOR.VINCULO}
 * (definido na V13).
 * </p>
 */
public enum VinculoProfessor {
    CLT_40H,
    CLT_30H,
    CLT_20H,
    HORISTA,
    TEMPORARIO,
    PRESTADOR_SERVICO
}
