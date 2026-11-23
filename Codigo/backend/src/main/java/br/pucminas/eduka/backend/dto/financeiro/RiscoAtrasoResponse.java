package br.pucminas.eduka.backend.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.financeiro.NivelRiscoAtraso;
import br.pucminas.eduka.backend.domain.financeiro.RegraRiscoAtraso;

/**
 * Risco de atraso de um aluno, com o porque. O nivel sai da soma dos pontos
 * de cada regra ({@link Fator}); {@code motivo} junta em uma frase as regras
 * que pontuaram, para a tela mostrar sem montar texto.
 *
 * @param pontos  soma dos pontos das regras, de 0 a 6
 * @param motivo  frase curta com o que levou ao nivel
 * @param fatores todas as regras, mesmo as que nao pontuaram, na ordem de
 *                {@link RegraRiscoAtraso}
 */
public record RiscoAtrasoResponse(
        UUID alunoId,
        String alunoNome,
        String alunoNumeroMatricula,
        NivelRiscoAtraso nivel,
        int pontos,
        String motivo,
        Indicadores indicadores,
        List<Fator> fatores) {

    /**
     * Numeros que as regras usam. A janela e de {@code desde} ate hoje, pelo
     * vencimento; cobranca vencida ainda em aberto entra mesmo se mais antiga.
     * Cobranca cancelada nao entra.
     *
     * @param cobrancasVencidas cobrancas com vencimento antes de hoje
     * @param atrasos           pagas depois do vencimento mais as vencidas em aberto
     * @param atrasosPagos      parte de {@code atrasos} ja paga
     * @param atrasosEmAberto   parte de {@code atrasos} ainda em aberto
     * @param diasMediosAtraso  media de dias de atraso de {@code atrasos}
     *                          (nas em aberto, ate hoje); nulo se nao houve atraso
     * @param valorEmAtraso     valor em aberto ja vencido
     * @param valorEmAberto     tudo em aberto, vencido ou nao
     */
    public record Indicadores(
            LocalDate desde,
            int cobrancasVencidas,
            int atrasos,
            int atrasosPagos,
            int atrasosEmAberto,
            Integer diasMediosAtraso,
            BigDecimal valorEmAtraso,
            BigDecimal valorEmAberto) {
    }

    /**
     * O que uma regra mediu e quantos pontos deu.
     *
     * @param pontos    de 0 a 2
     * @param descricao o valor medido em portugues, para exibir
     */
    public record Fator(RegraRiscoAtraso regra, int pontos, String descricao) {
    }
}
