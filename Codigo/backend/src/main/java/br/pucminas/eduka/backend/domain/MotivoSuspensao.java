package br.pucminas.eduka.backend.domain;

/** Por que a escola esta suspensa (coluna {@code ESCOLA.MOTIVO_SUSPENSAO}, V43). */
public enum MotivoSuspensao {
    /** Suspensa pelo Super Admin no Painel de Escolas. */
    MANUAL,
    /** Suspensa pela rotina: fatura da plataforma em aberto depois do prazo do aviso. */
    FATURA_EM_ATRASO
}
