package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Relatorio financeiro de um periodo (RF19, IU19), na escola do token.
 *
 * <p>
 * Cada numero responde a uma pergunta, e por isso cada um usa a sua data: o
 * recebido, as despesas pagas e o saldo contam pelo dia em que o dinheiro se
 * moveu, como no grafico do Inicio; o cobrado, o em aberto, a inadimplencia e as
 * despesas lancadas, pelo vencimento. Uma cobranca paga fora do mes em que vence
 * entra no recebido de um periodo e no resumo de outro.
 * </p>
 *
 * @param recebido          cobrancas pagas no periodo, online ou com baixa
 *                          manual, menos os estornos do periodo; a quantidade e
 *                          a de pagamentos
 * @param estornado         devolvido no periodo, ja descontado de
 *                          {@code recebido}, mesmo que o pagamento seja de antes
 * @param despesas          despesas lancadas: nao canceladas que vencem no
 *                          periodo
 * @param despesasPagas     despesas pagas no periodo, pelo dia do pagamento
 * @param saldo             recebido menos despesas pagas
 * @param receitasEDespesas um item por mes do periodo, mesmo os sem movimento,
 *                          do mais antigo ao mais recente, com o recebido e as
 *                          despesas pagas; os meses das pontas contam so os
 *                          dias do periodo
 * @param formasPagamento   PIX e BOLETO, sempre, e a baixa manual se houver, ja
 *                          sem os estornos
 */
public record RelatorioFinanceiroResponse(
        LocalDate de,
        LocalDate ate,
        Total recebido,
        Total estornado,
        Total despesas,
        Total despesasPagas,
        BigDecimal saldo,
        Inadimplencia inadimplencia,
        List<Mes> receitasEDespesas,
        List<Forma> formasPagamento,
        Resumo resumo) {

    public record Total(BigDecimal valor, long quantidade) {
    }

    /**
     * @param valor      cobrancas vencidas e nao pagas
     * @param cobrancas  quantas sao
     * @param percentual de 0 a 100, com uma casa: o valor sobre tudo o que ja
     *                   venceu no periodo (em atraso mais o pago com vencimento
     *                   antes de hoje); nulo enquanto nada venceu
     */
    public record Inadimplencia(BigDecimal valor, long cobrancas, BigDecimal percentual) {
    }

    public record Mes(YearMonth mes, BigDecimal receitas, BigDecimal despesas, BigDecimal saldo) {
    }

    /**
     * @param forma      PIX ou BOLETO; nula na baixa manual
     * @param valor      pode ser negativo, se so houve estorno nesta forma
     * @param quantidade pagamentos, sem contar os estornos
     * @param percentual com uma casa, sobre o recebido no periodo; nulo se o
     *                   recebido nao for positivo
     */
    public record Forma(FormaPagamento forma, BigDecimal valor, long quantidade, BigDecimal percentual) {
    }

    /**
     * Os tres agrupamentos do resumo: a tela troca de aba sem pedir de novo.
     *
     * @param porCurso do maior cobrado ao menor
     * @param porTipo  na ordem de {@code TipoCobranca}
     * @param porMes   pelo mes do vencimento, do mais antigo ao mais recente
     * @param total    soma de todas as linhas; a mesma em qualquer agrupamento
     */
    public record Resumo(List<Linha> porCurso, List<Linha> porTipo, List<Linha> porMes, Linha total) {
    }

    /**
     * Cobrancas nao canceladas que vencem no periodo.
     *
     * @param grupo         nome do curso ("Sem curso" para a cobranca avulsa),
     *                      nome do {@code TipoCobranca} ou mes (AAAA-MM); nulo
     *                      na linha de total
     * @param recebido      o que entrou por elas, ate agora, sem o estornado
     * @param emAberto      o que falta entrar, vencido ou nao; a estornada fica
     *                      encerrada e nao volta para ca
     * @param inadimplencia como {@link Inadimplencia#percentual}, so nestas
     */
    public record Linha(String grupo, BigDecimal cobrado, BigDecimal recebido, BigDecimal emAberto,
            BigDecimal inadimplencia) {
    }
}
