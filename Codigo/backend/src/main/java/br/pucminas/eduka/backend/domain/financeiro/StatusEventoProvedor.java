package br.pucminas.eduka.backend.domain.financeiro;

/**
 * Processamento de um evento do provedor (coluna {@code STATUS} de
 * {@code EVENTO_PROVEDOR}, V27).
 *
 * <p>
 * RECEBIDO vai para PROCESSANDO (somando uma tentativa) e termina em
 * PROCESSADO, IGNORADO ou ERRO. ERRO volta a PROCESSANDO pelo agendador ate o
 * limite de tentativas.
 * </p>
 */
public enum StatusEventoProvedor {
    RECEBIDO,
    PROCESSANDO,
    PROCESSADO,
    IGNORADO,
    ERRO
}
