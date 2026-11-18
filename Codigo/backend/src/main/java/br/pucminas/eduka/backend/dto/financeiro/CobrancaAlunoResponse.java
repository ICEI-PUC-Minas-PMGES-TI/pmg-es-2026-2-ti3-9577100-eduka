package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
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
 * @param pagamento         resumo do pagamento online confirmado; nulo
 *                          enquanto nao ha registro de pagamento (e na baixa
 *                          manual, que nao tem)
 * @param acoes             o que a tela mostra: {@value #PAGAR} na cobranca em
 *                          aberto, {@value #VER_COMPROVANTE} na paga com
 *                          pagamento registrado (RF16.1)
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
    public static final String VER_COMPROVANTE = "VER_COMPROVANTE";

    /** {@code pagamento} e o registro do pagamento online da cobranca, ou nulo. */
    public static CobrancaAlunoResponse de(Cobranca cobranca, PagamentoCobranca pagamento, LocalDate hoje) {
        StatusCobranca status = StatusCobranca.efetivo(cobranca.getStatus(), cobranca.getDataVencimento(), hoje);
        // So ha comprovante com o pagamento registrado (R28).
        boolean temComprovante = status == StatusCobranca.PAGA && pagamento != null;
        return new CobrancaAlunoResponse(cobranca.getId(), cobranca.getDescricao(), cobranca.getValor(),
                cobranca.getDataVencimento(), status.name(), situacaoPagamento(cobranca), cobranca.getFormaPagamento(),
                temComprovante ? PagamentoResumoResponse.de(pagamento) : null,
                status.emAberto() ? List.of(PAGAR) : temComprovante ? List.of(VER_COMPROVANTE) : List.of());
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
