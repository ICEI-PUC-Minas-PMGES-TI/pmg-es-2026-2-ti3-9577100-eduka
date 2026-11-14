package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Comprovante de pagamento emitido pelo Eduka (R30). Sem URL do provedor e com o
 * CPF mascarado.
 *
 * @param valorEstornado quanto do pago foi devolvido; zero se nada
 */
public record ComprovanteResponse(
        UUID numero,
        String beneficiarioNome,
        String beneficiarioDocumento,
        String pagadorNome,
        String pagadorDocumentoMascarado,
        String descricao,
        LocalDate vencimento,
        BigDecimal valorPago,
        LocalDate dataPagamento,
        FormaPagamento forma,
        String codigoTransacao,
        BigDecimal valorEstornado) {
}
