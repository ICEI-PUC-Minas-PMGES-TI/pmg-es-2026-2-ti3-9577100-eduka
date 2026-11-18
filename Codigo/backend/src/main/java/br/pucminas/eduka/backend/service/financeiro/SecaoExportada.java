package br.pucminas.eduka.backend.service.financeiro;

import java.util.List;

/**
 * Um conjunto de linhas da exportacao (cobrancas, pagamentos ou despesas),
 * pronto para virar uma secao do CSV ({@link PlanilhaCsv}) ou uma aba da
 * planilha ({@link PlanilhaFinanceiraXlsx}): os dois formatos saem das mesmas
 * linhas e somam o mesmo.
 *
 * <p>
 * As celulas sao texto, {@link java.math.BigDecimal}, {@link java.time.LocalDate}
 * ou nulo (vazia).
 * </p>
 *
 * @param titulo nome da secao no CSV e da aba na planilha
 * @param tabela nome da tabela do Excel: so letras sem acento, para formulas
 * @param vazio  o aviso da aba quando nao ha linhas
 */
record SecaoExportada(String titulo, String tabela, List<Coluna> colunas, List<Object[]> linhas, String vazio) {

    /**
     * @param opcoes valores aceitos na coluna (lista suspensa na planilha); vazia
     *               quando o texto e livre
     * @param somar  se a planilha mostra o total da coluna acima da tabela
     */
    record Coluna(String nome, List<String> opcoes, boolean somar) {

        static Coluna texto(String nome) {
            return new Coluna(nome, List.of(), false);
        }

        static Coluna lista(String nome, List<String> opcoes) {
            return new Coluna(nome, List.copyOf(opcoes), false);
        }

        static Coluna valor(String nome) {
            return new Coluna(nome, List.of(), true);
        }
    }

    String[] nomesDasColunas() {
        return colunas.stream().map(Coluna::nome).toArray(String[]::new);
    }
}
