package br.pucminas.eduka.backend.service.financeiro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.domain.financeiro.NivelRiscoAtraso;
import br.pucminas.eduka.backend.domain.financeiro.RegraRiscoAtraso;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.financeiro.RiscoAtrasoResponse.Fator;
import br.pucminas.eduka.backend.service.financeiro.RegrasRiscoAtraso.Cobranca;
import br.pucminas.eduka.backend.service.financeiro.RegrasRiscoAtraso.Resultado;

class RegrasRiscoAtrasoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 5);
    private static final BigDecimal MENSALIDADE = new BigDecimal("500.00");

    private static Cobranca paga(LocalDate vencimento, LocalDate pagaEm) {
        return new Cobranca(MENSALIDADE, vencimento, StatusCobranca.PAGA, pagaEm);
    }

    private static Cobranca aberta(LocalDate vencimento) {
        return new Cobranca(MENSALIDADE, vencimento, StatusCobranca.PENDENTE, null);
    }

    private static int pontos(Resultado r, RegraRiscoAtraso regra) {
        return r.fatores().stream().filter(f -> f.regra() == regra).mapToInt(Fator::pontos).sum();
    }

    @Test
    void semCobrancaEBaixo() {
        Resultado r = RegrasRiscoAtraso.avaliar(List.of(), HOJE);

        assertThat(r.nivel()).isEqualTo(NivelRiscoAtraso.BAIXO);
        assertThat(r.pontos()).isZero();
        assertThat(r.motivo()).isEqualTo("Sem cobranças vencidas nos últimos 12 meses");
        assertThat(r.indicadores().diasMediosAtraso()).isNull();
        assertThat(r.fatores()).extracting(Fator::regra).containsExactly(RegraRiscoAtraso.values());
    }

    @Test
    void quemPagaEmDiaEBaixo() {
        List<Cobranca> cobrancas = new ArrayList<>();
        for (int mes = 1; mes <= 6; mes++) {
            LocalDate vencimento = HOJE.minusMonths(mes);
            cobrancas.add(paga(vencimento, vencimento));
        }
        cobrancas.add(aberta(HOJE.plusDays(5)));

        Resultado r = RegrasRiscoAtraso.avaliar(cobrancas, HOJE);

        assertThat(r.nivel()).isEqualTo(NivelRiscoAtraso.BAIXO);
        assertThat(r.indicadores().cobrancasVencidas()).isEqualTo(6);
        assertThat(r.indicadores().atrasos()).isZero();
        // A que vence depois de hoje esta em aberto, mas nao em atraso.
        assertThat(r.indicadores().valorEmAberto()).isEqualByComparingTo("500.00");
        assertThat(r.indicadores().valorEmAtraso()).isEqualByComparingTo("0");
        assertThat(r.motivo()).isEqualTo("Pagou tudo em dia nos últimos 12 meses");
    }

    @Test
    void umAtrasoCurtoJaPagoNaoPontua() {
        Resultado r = RegrasRiscoAtraso.avaliar(List.of(paga(HOJE.minusMonths(2), HOJE.minusMonths(2).plusDays(3))),
                HOJE);

        assertThat(r.nivel()).isEqualTo(NivelRiscoAtraso.BAIXO);
        assertThat(r.pontos()).isZero();
        assertThat(r.indicadores().atrasos()).isEqualTo(1);
        assertThat(r.indicadores().diasMediosAtraso()).isEqualTo(3);
        assertThat(r.motivo()).isEqualTo("Só um atraso curto, nada vencido em aberto");
    }

    @Test
    void doisAtrasosDeDezDiasSaoMedio() {
        LocalDate v1 = HOJE.minusMonths(3);
        LocalDate v2 = HOJE.minusMonths(2);
        Resultado r = RegrasRiscoAtraso.avaliar(List.of(paga(v1, v1.plusDays(10)), paga(v2, v2.plusDays(10))), HOJE);

        assertThat(r.nivel()).isEqualTo(NivelRiscoAtraso.MEDIO);
        assertThat(pontos(r, RegraRiscoAtraso.ATRASOS_ANTERIORES)).isEqualTo(1);
        assertThat(pontos(r, RegraRiscoAtraso.DIAS_MEDIOS_ATRASO)).isEqualTo(1);
        assertThat(pontos(r, RegraRiscoAtraso.VALOR_EM_ABERTO)).isZero();
        assertThat(r.motivo())
                .isEqualTo("2 atrasos nos últimos 12 meses (2 pagos com atraso); Atraso médio de 10 dias");
    }

    @Test
    void umaMensalidadeAntigaEmAbertoEMedio() {
        Resultado r = RegrasRiscoAtraso.avaliar(List.of(aberta(HOJE.minusDays(40))), HOJE);

        assertThat(r.nivel()).isEqualTo(NivelRiscoAtraso.MEDIO);
        assertThat(r.pontos()).isEqualTo(3);
        assertThat(r.indicadores().atrasosEmAberto()).isEqualTo(1);
        assertThat(r.indicadores().diasMediosAtraso()).isEqualTo(40);
        // O dia e o mais pesado e vem primeiro no motivo.
        assertThat(r.motivo()).startsWith("Atraso médio de 40 dias; R$ 500,00 vencidos em aberto");
    }

    @Test
    void variasMensalidadesEmAbertoEAtrasosRepetidosSaoAlto() {
        List<Cobranca> cobrancas = new ArrayList<>();
        for (int mes = 3; mes <= 6; mes++) {
            LocalDate vencimento = HOJE.minusMonths(mes);
            cobrancas.add(paga(vencimento, vencimento.plusDays(20)));
        }
        cobrancas.add(aberta(HOJE.minusMonths(2)));
        cobrancas.add(aberta(HOJE.minusMonths(1)));

        Resultado r = RegrasRiscoAtraso.avaliar(cobrancas, HOJE);

        assertThat(r.nivel()).isEqualTo(NivelRiscoAtraso.ALTO);
        assertThat(r.pontos()).isEqualTo(6);
        assertThat(r.indicadores().atrasos()).isEqualTo(6);
        assertThat(r.indicadores().atrasosPagos()).isEqualTo(4);
        assertThat(r.indicadores().atrasosEmAberto()).isEqualTo(2);
        assertThat(r.indicadores().valorEmAtraso()).isEqualByComparingTo("1000.00");
        assertThat(r.fatores()).allMatch(f -> f.pontos() == 2);
        assertThat(r.motivo()).contains("6 atrasos nos últimos 12 meses (4 pagos com atraso, 2 ainda em aberto)")
                .contains("R$ 1.000,00 vencidos em aberto (2 cobranças médias ou mais)");
    }

    @Test
    void canceladaNaoConta() {
        Resultado r = RegrasRiscoAtraso.avaliar(
                List.of(new Cobranca(MENSALIDADE, HOJE.minusDays(60), StatusCobranca.CANCELADA, null)), HOJE);

        assertThat(r.nivel()).isEqualTo(NivelRiscoAtraso.BAIXO);
        assertThat(r.indicadores().cobrancasVencidas()).isZero();
    }

    @Test
    void atrasoPagoForaDaJanelaNaoContaMasDividaAntigaConta() {
        LocalDate antiga = HOJE.minusMonths(14);
        Resultado r = RegrasRiscoAtraso.avaliar(List.of(paga(antiga, antiga.plusDays(30)), aberta(antiga)), HOJE);

        assertThat(r.indicadores().atrasosPagos()).isZero();
        assertThat(r.indicadores().atrasosEmAberto()).isEqualTo(1);
        assertThat(r.indicadores().desde()).isEqualTo(HOJE.minusMonths(12));
    }

    @Test
    void pendenteQueVenceHojeAindaNaoEstaAtrasada() {
        Resultado r = RegrasRiscoAtraso.avaliar(List.of(aberta(HOJE)), HOJE);

        assertThat(r.indicadores().atrasos()).isZero();
        assertThat(r.indicadores().valorEmAtraso()).isEqualByComparingTo("0");
        assertThat(r.indicadores().valorEmAberto()).isEqualByComparingTo("500.00");
    }

    @Test
    void niveisPelosPontos() {
        assertThat(NivelRiscoAtraso.dePontos(1)).isEqualTo(NivelRiscoAtraso.BAIXO);
        assertThat(NivelRiscoAtraso.dePontos(2)).isEqualTo(NivelRiscoAtraso.MEDIO);
        assertThat(NivelRiscoAtraso.dePontos(3)).isEqualTo(NivelRiscoAtraso.MEDIO);
        assertThat(NivelRiscoAtraso.dePontos(4)).isEqualTo(NivelRiscoAtraso.ALTO);
    }
}
