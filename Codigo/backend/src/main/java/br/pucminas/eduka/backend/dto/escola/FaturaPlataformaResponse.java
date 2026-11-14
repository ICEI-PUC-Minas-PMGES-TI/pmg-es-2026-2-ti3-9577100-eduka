package br.pucminas.eduka.backend.dto.escola;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.escola.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.financeiro.PagamentoResumoResponse;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Fatura da plataforma (Fluxo A). Nada do provedor (id externo, URL) sai aqui.
 *
 * @param competencia       {@code yyyy-MM}
 * @param status            ja com EM_ATRASO no lugar de PENDENTE vencida
 * @param situacaoPagamento AGUARDANDO_PAGAMENTO, PROCESSANDO (gerando no
 *                          provedor), PAGA ou CANCELADA
 * @param pagamento         nulo enquanto nao paga
 * @param acoes             acoes permitidas a quem esta vendo, calculadas no
 *                          backend: {@value #PAGAR}, {@value #CANCELAR},
 *                          {@value #VER_COMPROVANTE}
 */
public record FaturaPlataformaResponse(
        UUID id,
        UUID escolaId,
        String escolaNome,
        String competencia,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        String status,
        String situacaoPagamento,
        FormaPagamento formaPagamento,
        PagamentoResumoResponse pagamento,
        List<String> acoes) {

    public static final String PAGAR = "PAGAR";
    public static final String CANCELAR = "CANCELAR";
    public static final String VER_COMPROVANTE = "VER_COMPROVANTE";

    private static final DateTimeFormatter COMPETENCIA = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Visao do Super Admin, que lanca e cancela. */
    public static FaturaPlataformaResponse paraSuperAdmin(FaturaPlataforma fatura, String escolaNome,
            LocalDate hoje) {
        List<String> acoes = new ArrayList<>();
        if (fatura.getStatus().emAberto() && fatura.getSituacaoProvedor() != SituacaoProvedor.EMITINDO) {
            acoes.add(CANCELAR);
        }
        if (fatura.getStatus() == StatusCobranca.PAGA) {
            acoes.add(VER_COMPROVANTE);
        }
        return de(fatura, escolaNome, hoje, acoes);
    }

    /** Visao do MASTER da escola, que paga. */
    public static FaturaPlataformaResponse paraEscola(FaturaPlataforma fatura, String escolaNome, LocalDate hoje) {
        List<String> acoes = new ArrayList<>();
        if (fatura.getStatus().emAberto()) {
            acoes.add(PAGAR);
        }
        if (fatura.getStatus() == StatusCobranca.PAGA) {
            acoes.add(VER_COMPROVANTE);
        }
        return de(fatura, escolaNome, hoje, acoes);
    }

    private static FaturaPlataformaResponse de(FaturaPlataforma fatura, String escolaNome, LocalDate hoje,
            List<String> acoes) {
        StatusCobranca status = StatusCobranca.efetivo(fatura.getStatus(), fatura.getDataVencimento(), hoje);
        PagamentoResumoResponse pagamento = fatura.getStatus() == StatusCobranca.PAGA
                ? new PagamentoResumoResponse(fatura.getValorPago(), fatura.getDataPagamento(),
                        fatura.getFormaPagamento(), BigDecimal.ZERO)
                : null;
        return new FaturaPlataformaResponse(fatura.getId(), fatura.getEscolaId(), escolaNome,
                fatura.getCompetencia().format(COMPETENCIA), fatura.getDescricao(), fatura.getValor(),
                fatura.getDataVencimento(), status.name(), situacaoPagamento(fatura), fatura.getFormaPagamento(),
                pagamento, List.copyOf(acoes));
    }

    private static String situacaoPagamento(FaturaPlataforma fatura) {
        return switch (fatura.getStatus()) {
            case PAGA -> "PAGA";
            case CANCELADA -> "CANCELADA";
            case PENDENTE, EM_ATRASO -> fatura.getSituacaoProvedor() == SituacaoProvedor.EMITINDO
                    ? "PROCESSANDO"
                    : "AGUARDANDO_PAGAMENTO";
        };
    }
}
