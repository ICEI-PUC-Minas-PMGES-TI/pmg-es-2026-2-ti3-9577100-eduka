package br.pucminas.eduka.backend.dto.dashboard;

/**
 * Resumo da escola para o MASTER: pessoas, estrutura academica, ocupacao e
 * desempenho geral. Tudo contado na escola do token.
 */
public record DashboardEscolaResponse(
        Pessoas pessoas,
        Estrutura estrutura,
        Ocupacao ocupacao,
        Desempenho desempenho) {

    /**
     * @param alunosAtivos   alunos com pelo menos uma matricula ATIVA
     * @param alunosSemTurma alunos sem nenhuma matricula ATIVA (mesma conta
     *                       do dashboard da secretaria)
     */
    public record Pessoas(
            long alunos,
            long alunosAtivos,
            long alunosSemTurma,
            long professoresAtivos,
            long professoresInativos,
            long secretarias,
            long financeiro) {
    }

    /**
     * @param periodoLetivoAtual nome do periodo que inclui hoje; nulo se
     *                           nenhum estiver em andamento
     */
    public record Estrutura(
            long cursos,
            long disciplinas,
            long turmasAtivas,
            long turmas,
            String periodoLetivoAtual) {
    }

    /**
     * Vagas das turmas ativas.
     *
     * @param percentual ocupadas sobre ofertadas, de 0 a 100 (arredondado);
     *                   nulo sem vagas ofertadas
     */
    public record Ocupacao(
            long vagasOfertadas,
            long matriculasAtivas,
            Integer percentual) {
    }

    /**
     * @param aproveitamento soma das notas sobre a soma do valor das
     *                       avaliacoes, so contando notas lancadas, de 0 a
     *                       100; nulo sem nenhuma nota
     */
    public record Desempenho(
            long avaliacoes,
            long notasLancadas,
            Integer aproveitamento) {
    }
}
