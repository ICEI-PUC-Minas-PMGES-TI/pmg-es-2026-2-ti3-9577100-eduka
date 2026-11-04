package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Painel financeiro da escola (RF20): o Inicio do FINANCEIRO (IU22) e a parte
 * financeira do Inicio do MASTER (IU05). Tudo na escola do token; "mes" e
 * "hoje" no fuso da escola.
 *
 * @param recebidoNoMes     cobrancas pagas neste mes, online ou com baixa manual
 * @param faltaReceber      cobrancas em aberto (pendentes e em atraso)
 * @param ultimosMeses      os 12 ultimos meses, do mais antigo ao atual
 * @param vencemNaSemana    em atraso e as que vencem ate daqui a 6 dias, pelo
 *                          vencimento; no maximo 10
 * @param ultimosPagamentos mais recentes primeiro; no maximo 10
 * @param ultimasAcoes      do LOG_AUDITORIA, mais recentes primeiro; no maximo 10
 */
public record DashboardFinanceiroResponse(
        Total recebidoNoMes,
        Total faltaReceber,
        Atraso emAtraso,
        DespesasAPagar despesasAPagar,
        List<Mes> ultimosMeses,
        List<CobrancaDaSemana> vencemNaSemana,
        List<PagamentoRecente> ultimosPagamentos,
        List<AcaoRecente> ultimasAcoes) {

    public record Total(BigDecimal valor, long quantidade) {
    }

    /**
     * @param alunos        alunos com pelo menos uma cobranca em atraso
     * @param inadimplencia de 0 a 100: o valor em atraso sobre tudo o que ja
     *                      venceu (em atraso mais o pago com vencimento antes
     *                      de hoje); nulo enquanto nada venceu
     */
    public record Atraso(long alunos, long cobrancas, BigDecimal valor, Integer inadimplencia) {
    }

    /**
     * @param valor              despesas a pagar com vencimento ate o fim do
     *                           mes, inclusive as atrasadas
     * @param proximoVencimento  o vencimento a pagar mais proximo, de hoje em
     *                           diante; nulo se nao houver
     */
    public record DespesasAPagar(BigDecimal valor, long quantidade, LocalDate proximoVencimento) {
    }

    /**
     * @param entrou recebido no mes (cobrancas pagas)
     * @param saiu   despesas pagas no mes
     */
    public record Mes(YearMonth mes, BigDecimal entrou, BigDecimal saiu) {
    }

    /** {@code status} ja com EM_ATRASO no lugar de PENDENTE vencida. */
    public record CobrancaDaSemana(UUID id, UUID alunoId, String alunoNome, String descricao, BigDecimal valor,
            LocalDate dataVencimento, StatusCobranca status) {
    }

    /**
     * @param valor  o valor pago no provedor; na baixa manual, o da cobranca
     * @param forma  PIX ou BOLETO; nula na baixa manual
     * @param origem ONLINE (confirmado pelo Asaas) ou MANUAL (baixa da escola)
     */
    public record PagamentoRecente(UUID cobrancaId, String alunoNome, String descricao, BigDecimal valor,
            FormaPagamento forma, OrigemPagamento origem, Instant pagoEm) {
    }

    public record AcaoRecente(UUID usuarioId, String usuarioNome, PerfilUsuario perfil, OperacaoAuditoria operacao,
            EntidadeAuditada entidade, UUID registroId, Instant dataHora) {
    }
}
