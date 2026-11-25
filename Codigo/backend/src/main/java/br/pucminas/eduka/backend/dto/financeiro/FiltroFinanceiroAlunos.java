package br.pucminas.eduka.backend.dto.financeiro;

/**
 * Filtros financeiros da lista de alunos (IU07), so do MASTER e do
 * FINANCEIRO. Todos opcionais e combinados com E entre si e com os filtros
 * academicos.
 *
 * @param situacao            nula traz todas
 * @param faixaAtraso         so vale com {@code situacao} EM_ATRASO (ou sem
 *                            situacao, quando implica o atraso)
 * @param venceEm7Dias        so quem tem pendente vencendo de hoje ate daqui a
 *                            7 dias
 * @param semMensalidadeNoMes so quem nao tem mensalidade com vencimento no mes
 *                            atual
 */
public record FiltroFinanceiroAlunos(
        FinanceiroDoAlunoResponse.Situacao situacao,
        FinanceiroDoAlunoResponse.FaixaAtraso faixaAtraso,
        boolean venceEm7Dias,
        boolean semMensalidadeNoMes) {

    public static final FiltroFinanceiroAlunos NENHUM = new FiltroFinanceiroAlunos(null, null, false, false);

    /** Se nenhum filtro financeiro foi pedido. */
    public boolean vazio() {
        return situacao == null && faixaAtraso == null && !venceEm7Dias && !semMensalidadeNoMes;
    }

    /** Se o aluno com essa situacao financeira passa pelos filtros. */
    public boolean aceita(FinanceiroDoAlunoResponse financeiro) {
        return (situacao == null || financeiro.situacao() == situacao)
                && (faixaAtraso == null || financeiro.faixaAtraso() == faixaAtraso)
                && (!venceEm7Dias || financeiro.venceEm7Dias())
                && (!semMensalidadeNoMes || !financeiro.mensalidadeNoMes());
    }
}
