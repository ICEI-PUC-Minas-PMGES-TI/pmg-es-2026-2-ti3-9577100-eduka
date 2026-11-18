package br.pucminas.eduka.backend.dto.escola;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aviso de suspensao para o MASTER: a escola sera suspensa em
 * {@code suspensaoEm} se a fatura continuar em aberto.
 *
 * @param diasRestantes de hoje ate {@code suspensaoEm}, no fuso da escola; 0
 *                      quando e hoje
 */
public record SuspensaoPrevistaResponse(
        LocalDate suspensaoEm,
        long diasRestantes,
        UUID faturaId,
        String faturaDescricao,
        BigDecimal faturaValor,
        LocalDate faturaVencimento) {
}
