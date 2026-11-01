package br.pucminas.eduka.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.dto.BoletimTurmaResponse.Frequencia;

/**
 * Calculo da frequencia do boletim: "sem aulas" nao tem percentual, e o
 * alerta so dispara com faltas estritamente acima de 25% das aulas.
 */
class BoletimFrequenciaTest {

    @Test
    void limiteUnicoEDe25PorCento() {
        assertThat(BoletimTurmaResponse.LIMITE_FALTAS_PERCENTUAL).isEqualTo(25);
    }

    @Test
    void semAulasNaoHaFrequencia() {
        assertThat(Frequencia.de(0, 0)).isNull();
        assertThat(Frequencia.somar(List.of())).isNull();
        assertThat(Frequencia.somar(Arrays.asList(null, null))).isNull();
    }

    @Test
    void semFaltasEFrequenciaCheiaSemAlerta() {
        Frequencia f = Frequencia.de(10, 0);

        assertThat(f.percentualFrequencia()).isEqualByComparingTo("100.0");
        assertThat(f.alertaFaltas()).isFalse();
    }

    @Test
    void todasAsFaltasZeramAPresencaEAlertam() {
        Frequencia f = Frequencia.de(10, 10);

        assertThat(f.percentualFrequencia()).isEqualByComparingTo("0.0");
        assertThat(f.alertaFaltas()).isTrue();
    }

    @Test
    void exatamente25PorCentoDeFaltasNaoAlerta() {
        Frequencia f = Frequencia.de(20, 5);

        assertThat(f.faltas()).isEqualTo(5);
        assertThat(f.percentualFrequencia()).isEqualByComparingTo("75.0");
        assertThat(f.alertaFaltas()).isFalse();
    }

    @Test
    void vinteEcincoVirgulaUmPorCentoDeFaltasAlerta() {
        Frequencia f = Frequencia.de(1000, 251);

        assertThat(f.percentualFrequencia()).isEqualByComparingTo("74.9");
        assertThat(f.alertaFaltas()).isTrue();
    }

    @Test
    void alertaNaoDependeDoArredondamentoDaPresenca() {
        // 1 falta em 3 aulas = 33,3%: acima. 249 em 1000 = 24,9%: abaixo, mesmo exibindo 75,1.
        assertThat(Frequencia.de(3, 1).alertaFaltas()).isTrue();
        Frequencia abaixo = Frequencia.de(1000, 249);
        assertThat(abaixo.percentualFrequencia()).isEqualByComparingTo("75.1");
        assertThat(abaixo.alertaFaltas()).isFalse();
    }

    @Test
    void presencaTemUmaCasaDecimalArredondadaParaCima() {
        assertThat(Frequencia.de(3, 1).percentualFrequencia()).isEqualByComparingTo("66.7");
        assertThat(Frequencia.de(8, 1).percentualFrequencia()).isEqualByComparingTo("87.5");
    }

    @Test
    void geralSomaAulasEFaltasIgnorandoDisciplinasSemAula() {
        Frequencia geral = Frequencia.somar(Arrays.asList(Frequencia.de(8, 1), null, Frequencia.de(12, 4)));

        assertThat(geral.totalAulas()).isEqualTo(20);
        assertThat(geral.faltas()).isEqualTo(5);
        assertThat(geral.percentualFrequencia()).isEqualByComparingTo("75.0");
        assertThat(geral.alertaFaltas()).isFalse();
    }

    @Test
    void geralPodeAlertarMesmoComDisciplinaDentroDoLimite() {
        // 3/10 alerta sozinha; 0/10 nao, mas a soma 3/20 = 15% fica abaixo.
        assertThat(Frequencia.somar(List.of(Frequencia.de(10, 3), Frequencia.de(10, 0))).alertaFaltas()).isFalse();
        assertThat(Frequencia.somar(List.of(Frequencia.de(10, 3), Frequencia.de(10, 3))).alertaFaltas()).isTrue();
    }
}
