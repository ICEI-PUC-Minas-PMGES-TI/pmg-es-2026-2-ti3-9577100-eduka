package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Cobranca vista pelo aluno no portal, com o que a tela pode fazer com ela.
 * Nada do provedor (id externo, URL) sai aqui.
 *
 * @param status            ja com EM_ATRASO no lugar de PENDENTE vencida
 * @param situacaoPagamento AGUARDANDO_PAGAMENTO, PROCESSANDO (gerando no
 *                          provedor), PAGA ou CANCELADA
 * @param formaPagamento    forma escolhida no ultimo Pagar; nula se ninguem
 *                          clicou
 * @param pagamento         resumo do pagamento confirmado; nulo enquanto nao
 *                          ha registro de pagamento (RF16.1)
 * @param acoes             o que a tela mostra: {@value #PAGAR}
 */
public record CobrancaAlunoResponse(
        UUID id,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        String status,
        String situacaoPagamento,
        FormaPagamento formaPagamento,
        PagamentoResumoResponse pagamento,
        List<String> acoes) {

    public static final String PAGAR = "PAGAR";

    public static CobrancaAlunoResponse de(Cobranca cobranca, LocalDate hoje) {
        StatusCobranca status = StatusCobranca.efetivo(cobranca.getStatus(), cobranca.getDataVencimento(), hoje);
        return new CobrancaAlunoResponse(cobranca.getId(), cobranca.getDescricao(), cobranca.getValor(),
                cobranca.getDataVencimento(), status.name(), situacaoPagamento(cobranca), cobranca.getFormaPagamento(),
                null, status.emAberto() ? List.of(PAGAR) : List.of());
    }

    private static String situacaoPagamento(Cobranca cobranca) {
        return switch (cobranca.getStatus()) {
            case PAGA -> "PAGA";
            case CANCELADA -> "CANCELADA";
            case PENDENTE, EM_ATRASO -> cobranca.getSituacaoProvedor() == SituacaoProvedor.EMITINDO
                    ? "PROCESSANDO"
                    : "AGUARDANDO_PAGAMENTO";
        };
    }
}
