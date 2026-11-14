package br.pucminas.eduka.backend.domain.auditoria;

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
    ALTERAR_SITUACAO,
    /** Afastamento ou saida: o login para de funcionar e o registro fica (V44). */
    INATIVAR,
    /** Exclusao fisica, como a do professor que saiu da escola (V44). */
    EXCLUIR,
    /** Nova senha provisoria e novo e-mail de primeiro acesso (V44). */
    REENVIAR_ACESSO,
    /** As notas da avaliacao passaram a aparecer no boletim (V44). */
    PUBLICAR_NOTAS,
    /** A escola foi suspensa, pelo SUPER_ADMIN ou por fatura em atraso (V44). */
    SUSPENDER,
    /** Os dados pessoais do aluno foram exportados a pedido do titular (LGPD, V45). */
    EXPORTAR_DADOS,
    /** O dado pessoal do aluno foi apagado e o historico financeiro ficou (LGPD, V45). */
    ANONIMIZAR
}
