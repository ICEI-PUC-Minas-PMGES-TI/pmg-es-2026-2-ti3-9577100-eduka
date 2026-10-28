package br.pucminas.eduka.backend.domain;

/**
 * O que foi feito no registro auditado (coluna {@code LOG_AUDITORIA.OPERACAO},
 * V22).
 */
public enum OperacaoAuditoria {
    CRIAR,
    EDITAR,
    CANCELAR,
    /** Baixa manual de pagamento (V24). */
    PAGAR,
    /** Pagamento confirmado pelo provedor, sem autor (V32). */
    CONFIRMAR_PAGAMENTO,
    /** O provedor recebeu o pagamento de uma cobranca ja cancelada, que voltou a PAGA (V33). */
    PAGO_APOS_CANCELAR,
    /** Desfazer um pagamento ja registrado; tambem o estorno feito no provedor (V34). */
    ESTORNAR,
    /** O pagador contestou o pagamento no provedor (chargeback), sem autor (V34). */
    CONTESTAR,
    /** O MASTER pediu a criacao da conta de recebimento da escola (V34). */
    ATIVAR,
    /** O provedor mudou a situacao da conta de recebimento, sem autor (V34). */
    ALTERAR_SITUACAO
}
