package br.pucminas.eduka.backend.service.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.pucminas.eduka.backend.domain.financeiro.NivelRiscoAtraso;
import br.pucminas.eduka.backend.domain.financeiro.RegraRiscoAtraso;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.financeiro.RiscoAtrasoResponse.Fator;
import br.pucminas.eduka.backend.dto.financeiro.RiscoAtrasoResponse.Indicadores;

/**
 * Regras explicaveis do risco de atraso de um aluno. Sem banco e sem relogio:
 * recebe as cobrancas e o dia de hoje, devolve nivel, pontos e o porque.
 *
 * <p>
 * Tres regras, de 0 a 2 pontos cada; a soma da o nivel
 * ({@link NivelRiscoAtraso#dePontos}):
 * </p>
 * <ul>
 * <li><b>Atrasos anteriores</b>: cobrancas pagas depois do vencimento mais as
 * vencidas ainda em aberto, nos ultimos {@link #MESES_DA_JANELA} meses. Ate 1
 * atraso: 0; 2 ou 3: 1 ponto; {@value #ATRASOS_PARA_2_PONTOS} ou mais: 2.</li>
 * <li><b>Dias medios de atraso</b>: media dos dias de atraso dessas cobrancas
 * (nas em aberto, contando ate hoje). Ate {@value #DIAS_TOLERADOS} dias: 0;
 * ate {@value #DIAS_PARA_1_PONTO_ATE} dias: 1 ponto; mais que isso: 2.</li>
 * <li><b>Valor em aberto</b>: o que esta em aberto e ja venceu. Nada: 0;
 * menos de {@value #COBRANCAS_MEDIAS_PARA_2_PONTOS} vezes o valor medio das
 * cobrancas do aluno: 1 ponto; isso ou mais: 2. Comparar com o proprio aluno
 * evita um valor fixo em reais, que nao serve a escolas com mensalidades
 * diferentes.</li>
 * </ul>
 *
 * <p>
 * Cobranca cancelada nao entra. A que ainda nao venceu so entra no valor em
 * aberto e no valor medio.
 * </p>
 */
public final class RegrasRiscoAtraso {

    /** Janela do historico, pelo vencimento. Vencida em aberto entra mesmo se mais antiga. */
    public static final int MESES_DA_JANELA = 12;

    static final int ATRASOS_PARA_1_PONTO = 2;
    static final int ATRASOS_PARA_2_PONTOS = 4;
    static final int DIAS_TOLERADOS = 5;
    static final int DIAS_PARA_1_PONTO_ATE = 15;
    static final int COBRANCAS_MEDIAS_PARA_2_PONTOS = 2;

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private RegrasRiscoAtraso() {
    }

    /**
     * Cobranca do aluno com o que as regras precisam.
     *
     * @param status gravado; PENDENTE vencida e tratada como em atraso
     * @param pagaEm dia em que foi paga, no fuso da escola; nulo se nao paga ou
     *               se a data nao e conhecida (conta como paga em dia)
     */
    public record Cobranca(BigDecimal valor, LocalDate vencimento, StatusCobranca status, LocalDate pagaEm) {
    }

    /** Resultado das regras, sem os dados do aluno. */
    public record Resultado(NivelRiscoAtraso nivel, int pontos, String motivo, Indicadores indicadores,
            List<Fator> fatores) {
    }

    /** Inicio da janela do historico para {@code hoje}. */
    public static LocalDate inicioDaJanela(LocalDate hoje) {
        return hoje.minusMonths(MESES_DA_JANELA);
    }

    public static Resultado avaliar(List<Cobranca> cobrancas, LocalDate hoje) {
        LocalDate desde = inicioDaJanela(hoje);
        int vencidas = 0;
        int atrasosPagos = 0;
        int atrasosEmAberto = 0;
        long diasDeAtraso = 0;
        int consideradas = 0;
        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal valorEmAtraso = BigDecimal.ZERO;
        BigDecimal valorEmAberto = BigDecimal.ZERO;

        for (Cobranca c : cobrancas) {
            if (c.status() == StatusCobranca.CANCELADA) {
                continue;
            }
            boolean emAberto = c.status().emAberto();
            boolean venceu = c.vencimento().isBefore(hoje);
            // Fora da janela so a vencida em aberto conta: e divida que continua.
            if (c.vencimento().isBefore(desde) && !(emAberto && venceu)) {
                continue;
            }
            consideradas++;
            valorTotal = valorTotal.add(c.valor());
            if (emAberto) {
                valorEmAberto = valorEmAberto.add(c.valor());
            }
            if (!venceu) {
                continue;
            }
            vencidas++;
            if (emAberto) {
                atrasosEmAberto++;
                diasDeAtraso += ChronoUnit.DAYS.between(c.vencimento(), hoje);
                valorEmAtraso = valorEmAtraso.add(c.valor());
            } else if (c.pagaEm() != null && c.pagaEm().isAfter(c.vencimento())) {
                atrasosPagos++;
                diasDeAtraso += ChronoUnit.DAYS.between(c.vencimento(), c.pagaEm());
            }
        }

        int atrasos = atrasosPagos + atrasosEmAberto;
        Integer diasMedios = atrasos == 0 ? null
                : BigDecimal.valueOf(diasDeAtraso).divide(BigDecimal.valueOf(atrasos), 0, RoundingMode.HALF_UP)
                        .intValue();
        BigDecimal valorMedio = consideradas == 0 ? BigDecimal.ZERO
                : valorTotal.divide(BigDecimal.valueOf(consideradas), 2, RoundingMode.HALF_UP);

        List<Fator> fatores = List.of(
                atrasosAnteriores(atrasos, atrasosPagos, atrasosEmAberto),
                diasMedios(diasMedios),
                valorEmAberto(valorEmAtraso, valorMedio));
        int pontos = fatores.stream().mapToInt(Fator::pontos).sum();
        NivelRiscoAtraso nivel = NivelRiscoAtraso.dePontos(pontos);
        Indicadores indicadores = new Indicadores(desde, vencidas, atrasos, atrasosPagos, atrasosEmAberto, diasMedios,
                valorEmAtraso, valorEmAberto);
        return new Resultado(nivel, pontos, motivo(fatores, vencidas, atrasos), indicadores, fatores);
    }

    private static Fator atrasosAnteriores(int atrasos, int pagos, int emAberto) {
        int pontos = atrasos >= ATRASOS_PARA_2_PONTOS ? 2 : atrasos >= ATRASOS_PARA_1_PONTO ? 1 : 0;
        String descricao;
        if (atrasos == 0) {
            descricao = "Nenhum atraso nos últimos " + MESES_DA_JANELA + " meses";
        } else {
            List<String> partes = new ArrayList<>();
            if (pagos > 0) {
                partes.add(pagos + (pagos == 1 ? " pago com atraso" : " pagos com atraso"));
            }
            if (emAberto > 0) {
                partes.add(emAberto + " ainda em aberto");
            }
            descricao = atrasos + (atrasos == 1 ? " atraso" : " atrasos") + " nos últimos " + MESES_DA_JANELA
                    + " meses (" + String.join(", ", partes) + ")";
        }
        return new Fator(RegraRiscoAtraso.ATRASOS_ANTERIORES, pontos, descricao);
    }

    private static Fator diasMedios(Integer dias) {
        if (dias == null) {
            return new Fator(RegraRiscoAtraso.DIAS_MEDIOS_ATRASO, 0, "Sem dias de atraso");
        }
        int pontos = dias > DIAS_PARA_1_PONTO_ATE ? 2 : dias > DIAS_TOLERADOS ? 1 : 0;
        String descricao = "Atraso médio de " + dias + (dias == 1 ? " dia" : " dias");
        return new Fator(RegraRiscoAtraso.DIAS_MEDIOS_ATRASO, pontos, descricao);
    }

    private static Fator valorEmAberto(BigDecimal emAtraso, BigDecimal valorMedio) {
        if (emAtraso.signum() == 0) {
            return new Fator(RegraRiscoAtraso.VALOR_EM_ABERTO, 0, "Nada vencido em aberto");
        }
        BigDecimal limite = valorMedio.multiply(BigDecimal.valueOf(COBRANCAS_MEDIAS_PARA_2_PONTOS));
        boolean alto = valorMedio.signum() > 0 && emAtraso.compareTo(limite) >= 0;
        String descricao = moeda(emAtraso) + " vencidos em aberto"
                + (alto ? " (" + COBRANCAS_MEDIAS_PARA_2_PONTOS + " cobranças médias ou mais)"
                        : " (menos de " + COBRANCAS_MEDIAS_PARA_2_PONTOS + " cobranças médias)");
        return new Fator(RegraRiscoAtraso.VALOR_EM_ABERTO, alto ? 2 : 1, descricao);
    }

    /** As regras que pontuaram, da que mais pontuou para a que menos; sem nenhuma, o porque do baixo. */
    private static String motivo(List<Fator> fatores, int vencidas, int atrasos) {
        List<String> pontuaram = fatores.stream()
                .filter(f -> f.pontos() > 0)
                .sorted((a, b) -> Integer.compare(b.pontos(), a.pontos()))
                .map(Fator::descricao)
                .toList();
        if (!pontuaram.isEmpty()) {
            return String.join("; ", pontuaram);
        }
        if (vencidas == 0) {
            return "Sem cobranças vencidas nos últimos " + MESES_DA_JANELA + " meses";
        }
        // Pode ter havido um atraso curto e isolado, que nao pontua.
        return atrasos == 0 ? "Pagou tudo em dia nos últimos " + MESES_DA_JANELA + " meses"
                : "Só um atraso curto, nada vencido em aberto";
    }

    private static String moeda(BigDecimal valor) {
        return String.format(PT_BR, "R$ %,.2f", valor);
    }
}
