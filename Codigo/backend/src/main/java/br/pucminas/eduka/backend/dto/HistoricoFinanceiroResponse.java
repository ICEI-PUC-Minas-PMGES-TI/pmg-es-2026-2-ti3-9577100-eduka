package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.StatusCobranca;

/**
 * Historico financeiro de um aluno: resumo e todas as cobrancas, da mais
 * recente para a mais antiga (pelo vencimento). Cada cobranca paga traz o
 * pagamento (online ou baixa manual) e, no online, o link do comprovante na
 * rota do perfil de quem pediu.
 */
public record HistoricoFinanceiroResponse(
        UUID alunoId,
        String alunoNome,
        String alunoNumeroMatricula,
        Resumo resumo,
        List<CobrancaResponse> cobrancas) {

    /**
     * Totais por situacao efetiva (PENDENTE vencida ja conta como em atraso).
     *
     * @param valorEmAberto pendentes + em atraso
     * @param valorEmAtraso parte do em aberto que ja venceu
     * @param valorPago     cobrancas pagas, sem o que foi estornado
     */
    public record Resumo(
            BigDecimal valorEmAberto,
            BigDecimal valorEmAtraso,
            BigDecimal valorPago,
            long quantidadePendente,
            long quantidadeEmAtraso,
            long quantidadePaga,
            long quantidadeCancelada) {

        public static Resumo de(List<CobrancaResponse> cobrancas) {
            BigDecimal emAberto = BigDecimal.ZERO;
            BigDecimal emAtraso = BigDecimal.ZERO;
            BigDecimal pago = BigDecimal.ZERO;
            long pendentes = 0;
            long atrasadas = 0;
            long pagas = 0;
            long canceladas = 0;
            for (CobrancaResponse cobranca : cobrancas) {
                StatusCobranca status = cobranca.status();
                switch (status) {
                    case PENDENTE -> {
                        emAberto = emAberto.add(cobranca.valor());
                        pendentes++;
                    }
                    case EM_ATRASO -> {
                        emAberto = emAberto.add(cobranca.valor());
                        emAtraso = emAtraso.add(cobranca.valor());
                        atrasadas++;
                    }
                    case PAGA -> {
                        pago = pago.add(cobranca.valor());
                        if (cobranca.pagamento() != null) {
                            pago = pago.subtract(cobranca.pagamento().valorEstornado());
                        }
                        pagas++;
                    }
                    case CANCELADA -> canceladas++;
                }
            }
            return new Resumo(emAberto, emAtraso, pago, pendentes, atrasadas, pagas, canceladas);
        }
    }
}
