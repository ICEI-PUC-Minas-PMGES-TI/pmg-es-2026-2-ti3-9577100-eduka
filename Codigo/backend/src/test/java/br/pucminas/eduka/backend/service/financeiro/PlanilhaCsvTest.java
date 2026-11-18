package br.pucminas.eduka.backend.service.financeiro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.pucminas.eduka.backend.dto.comum.ArquivoCsv.Separador;

class PlanilhaCsvTest {

    private static String texto(PlanilhaCsv planilha) {
        byte[] bytes = planilha.paraBytes();
        return new String(Arrays.copyOfRange(bytes, 3, bytes.length), StandardCharsets.UTF_8);
    }

    private static String celula(Separador separador, Object valor) {
        PlanilhaCsv planilha = new PlanilhaCsv(separador);
        planilha.linha(valor);
        return texto(planilha).replace("\r\n", "");
    }

    @Test
    void comecaComOBomDoUtf8ParaOExcelLerOsAcentos() {
        PlanilhaCsv planilha = new PlanilhaCsv(Separador.PONTO_E_VIRGULA);
        planilha.linha("Situação");

        byte[] bytes = planilha.paraBytes();

        assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(texto(planilha)).isEqualTo("Situação\r\n");
    }

    @Test
    void secoesVemComTituloECabecalhoSeparadasPorUmaLinhaEmBranco() {
        PlanilhaCsv planilha = new PlanilhaCsv(Separador.PONTO_E_VIRGULA);
        planilha.secao("Cobranças", "Aluno", "Valor");
        planilha.linha("Ana", new BigDecimal("10.00"));
        planilha.secao("Despesas", "Descrição");

        assertThat(texto(planilha)).isEqualTo(
                "Cobranças\r\nAluno;Valor\r\nAna;10,00\r\n\r\nDespesas\r\nDescrição\r\n");
    }

    @Test
    void valorUsaVirgulaComPontoEVirgulaEPontoComVirgula() {
        BigDecimal valor = new BigDecimal("1234.50");

        assertThat(celula(Separador.PONTO_E_VIRGULA, valor)).isEqualTo("1234,50");
        assertThat(celula(Separador.VIRGULA, valor)).isEqualTo("1234.50");
    }

    @Test
    void dataVemEmDiaMesAnoENuloViraCelulaVazia() {
        PlanilhaCsv planilha = new PlanilhaCsv(Separador.PONTO_E_VIRGULA);
        planilha.linha(LocalDate.of(2026, 3, 5), null, "fim");

        assertThat(texto(planilha)).isEqualTo("05/03/2026;;fim\r\n");
    }

    @Test
    void poeAspasSoQuandoOTextoPrecisa() {
        assertThat(celula(Separador.PONTO_E_VIRGULA, "Silva; Ana")).isEqualTo("\"Silva; Ana\"");
        assertThat(celula(Separador.PONTO_E_VIRGULA, "Silva, Ana")).isEqualTo("Silva, Ana");
        assertThat(celula(Separador.VIRGULA, "Silva, Ana")).isEqualTo("\"Silva, Ana\"");
        assertThat(celula(Separador.VIRGULA, "o \"grande\"")).isEqualTo("\"o \"\"grande\"\"\"");
        assertThat(celula(Separador.VIRGULA, "duas\nlinhas")).isEqualTo("\"duas\nlinhas\"");
    }

    @ParameterizedTest
    @ValueSource(strings = { "=HYPERLINK(\"http://mal\")", "+1+1", "-1+1", "@SOMA(A1)", "\tcmd" })
    void textoQueAPlanilhaExecutariaComoFormulaGanhaApostrofo(String perigoso) {
        assertThat(celula(Separador.VIRGULA, perigoso).replace("\"", "")).startsWith("'");
    }

    @Test
    void valorNegativoNaoETratadoComoFormula() {
        assertThat(celula(Separador.VIRGULA, new BigDecimal("-10.00"))).isEqualTo("-10.00");
    }
}
