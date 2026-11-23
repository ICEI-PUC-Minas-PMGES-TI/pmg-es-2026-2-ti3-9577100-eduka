package br.pucminas.eduka.backend.service.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Forma;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Inadimplencia;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Linha;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Mes;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Resumo;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Total;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.service.financeiro.SecaoExportada.Coluna;

/** A planilha relida pelo POI, com as formulas calculadas como o Excel faria. */
class PlanilhaFinanceiraXlsxTest {

    private static final LocalDate DE = LocalDate.of(2026, 8, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 9, 30);
    private static final PlanilhaFinanceiraXlsx.Escola ESCOLA = new PlanilhaFinanceiraXlsx.Escola(
            "Colégio São José", "CNPJ 12.345.678/0001-90  ·  Belo Horizonte/MG");
    private static final String PERIODO = "Período: 01/08/2026 a 30/09/2026  ·  Gerado em 15/11/2026 às 10:30";

    private static BigDecimal reais(String valor) {
        return new BigDecimal(valor);
    }

    private static Linha linha(String grupo, String cobrado, String recebido, String emAberto, String inadimplencia) {
        return new Linha(grupo, reais(cobrado), reais(recebido), reais(emAberto),
                inadimplencia == null ? null : reais(inadimplencia));
    }

    private static RelatorioFinanceiroResponse relatorio() {
        Linha total = linha(null, "350.00", "300.00", "50.00", "14.3");
        return new RelatorioFinanceiroResponse(DE, ATE,
                new Total(reais("300.00"), 2), new Total(reais("20.00"), 1),
                new Total(reais("150.00"), 2), new Total(reais("100.00"), 1),
                reais("200.00"),
                new Inadimplencia(reais("50.00"), 1, reais("14.3")),
                List.of(new Mes(YearMonth.of(2026, 8), reais("320.00"), reais("100.00"), reais("220.00")),
                        new Mes(YearMonth.of(2026, 9), reais("-20.00"), reais("0.00"), reais("-20.00"))),
                List.of(new Forma(FormaPagamento.PIX, reais("200.00"), 1, reais("66.7")),
                        new Forma(FormaPagamento.BOLETO, reais("100.00"), 1, reais("33.3"))),
                new Resumo(List.of(linha("Ensino Médio", "350.00", "300.00", "50.00", "14.3")),
                        List.of(linha("Mensalidade", "350.00", "300.00", "50.00", "14.3")),
                        List.of(linha("Agosto de 2026", "350.00", "300.00", "50.00", null)),
                        total));
    }

    private static SecaoExportada cobrancas(List<Object[]> linhas) {
        return new SecaoExportada("Cobranças", "Cobrancas",
                List.of(Coluna.texto("Aluno"), Coluna.valor("Valor"), Coluna.texto("Vencimento"),
                        Coluna.lista("Situação", List.of("Pendente", "Paga"))),
                linhas, "Nenhuma cobrança vence neste período.");
    }

    private static XSSFWorkbook gerar(SecaoExportada... secoes) throws IOException {
        PlanilhaFinanceiraXlsx planilha = new PlanilhaFinanceiraXlsx(ESCOLA, PERIODO);
        planilha.resumo(relatorio(), relatorio().resumo(), f -> f.forma() == null ? "Baixa manual" : f.forma().name());
        for (SecaoExportada secao : secoes) {
            planilha.aba(secao);
        }
        XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(planilha.paraBytes()));
        XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
        return workbook;
    }

    private static List<Object[]> duasCobrancas() {
        List<Object[]> linhas = new ArrayList<>();
        linhas.add(new Object[] { "Ana Souza", reais("200.00"), LocalDate.of(2026, 8, 10), "Paga" });
        linhas.add(new Object[] { "=HYPERLINK(\"http://mal\")", reais("150.50"), LocalDate.of(2026, 9, 10), "Pendente" });
        return linhas;
    }

    /** O texto mostrado na celula, ja formatado e com a formula calculada. */
    private static String mostrado(XSSFSheet aba, int linha, int coluna) {
        Row row = aba.getRow(linha);
        Cell cell = row == null ? null : row.getCell(coluna);
        return cell == null ? "" : new DataFormatter().formatCellValue(cell,
                aba.getWorkbook().getCreationHelper().createFormulaEvaluator());
    }

    /** A primeira linha cuja coluna A e {@code texto}. */
    private static int linhaCom(XSSFSheet aba, String texto) {
        for (Row row : aba) {
            Cell cell = row.getCell(0);
            if (cell != null && cell.getCellType() == CellType.STRING && texto.equals(cell.getStringCellValue())) {
                return row.getRowNum();
            }
        }
        throw new AssertionError("Linha nao encontrada: " + texto);
    }

    private static double numero(XSSFSheet aba, int linha, int coluna) {
        Cell cell = aba.getRow(linha).getCell(coluna);
        return cell.getNumericCellValue();
    }

    @Test
    void todaAbaAbreComAEscolaEOPeriodo() throws IOException {
        try (XSSFWorkbook workbook = gerar(cobrancas(duasCobrancas()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("Resumo");
            assertThat(workbook.getSheetName(1)).isEqualTo("Cobranças");
            for (XSSFSheet aba : List.of(workbook.getSheetAt(0), workbook.getSheetAt(1))) {
                assertThat(mostrado(aba, 1, 0)).isEqualTo("Colégio São José");
                assertThat(mostrado(aba, 2, 0)).isEqualTo("CNPJ 12.345.678/0001-90  ·  Belo Horizonte/MG");
                assertThat(mostrado(aba, 3, 0)).isEqualTo(PERIODO);
                assertThat(aba.getHeader().getLeft()).isEqualTo("Colégio São José");
            }
            assertThat(workbook.getProperties().getExtendedProperties().getUnderlyingProperties().getCompany())
                    .isEqualTo("Colégio São José");
        }
    }

    @Test
    void resumoTrazOsNumerosDaTelaComSaldoETotaisPorFormula() throws IOException {
        try (XSSFWorkbook workbook = gerar()) {
            XSSFSheet resumo = workbook.getSheet("Resumo");

            int recebido = linhaCom(resumo, "Total recebido");
            assertThat(numero(resumo, recebido, 1)).isEqualTo(300.00);
            int saldo = linhaCom(resumo, "Saldo do período");
            assertThat(resumo.getRow(saldo).getCell(1).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(numero(resumo, saldo, 1)).isEqualTo(200.00);
            int inadimplencia = linhaCom(resumo, "Inadimplência");
            assertThat(numero(resumo, inadimplencia, 1)).isEqualTo(0.143);
            assertThat(resumo.getRow(inadimplencia).getCell(1).getCellStyle().getDataFormatString()).isEqualTo("0.0%");

            int agosto = linhaCom(resumo, "Agosto de 2026");
            assertThat(numero(resumo, agosto, 3)).isEqualTo(220.00);
            assertThat(mostrado(resumo, agosto + 1, 0)).isEqualTo("Setembro de 2026");
            int totalDosMeses = agosto + 2;
            assertThat(mostrado(resumo, totalDosMeses, 0)).isEqualTo("Total do período");
            assertThat(numero(resumo, totalDosMeses, 1)).isEqualTo(300.00);
            assertThat(numero(resumo, totalDosMeses, 3)).isEqualTo(200.00);

            int pix = linhaCom(resumo, "PIX");
            assertThat(numero(resumo, pix, 3)).isCloseTo(0.6667, offset(0.0001));
            assertThat(numero(resumo, pix + 2, 1)).isEqualTo(300.00);

            int curso = linhaCom(resumo, "Ensino Médio");
            assertThat(numero(resumo, curso + 1, 1)).isEqualTo(350.00);
            assertThat(numero(resumo, curso + 1, 4)).isEqualTo(0.143);
        }
    }

    @Test
    void editarUmValorRecalculaOSaldo() throws IOException {
        try (XSSFWorkbook workbook = gerar()) {
            XSSFSheet resumo = workbook.getSheet("Resumo");
            int pagas = linhaCom(resumo, "Despesas pagas");
            resumo.getRow(pagas).getCell(1).setCellValue(250.00);

            XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);

            assertThat(numero(resumo, linhaCom(resumo, "Saldo do período"), 1)).isEqualTo(50.00);
        }
    }

    @Test
    void abaDeLinhasEUmaTabelaDoExcelComFiltroListaETotaisQueSeguemOFiltro() throws IOException {
        try (XSSFWorkbook workbook = gerar(cobrancas(duasCobrancas()))) {
            XSSFSheet aba = workbook.getSheet("Cobranças");

            assertThat(aba.getTables()).hasSize(1);
            XSSFTable tabela = aba.getTables().getFirst();
            assertThat(tabela.getName()).isEqualTo("Cobrancas");
            assertThat(tabela.getColumns()).extracting(c -> c.getName())
                    .containsExactly("Aluno", "Valor", "Vencimento", "Situação");
            assertThat(tabela.getCTTable().getAutoFilter()).isNotNull();
            assertThat(tabela.getEndCellReference().getRow() - tabela.getStartCellReference().getRow()).isEqualTo(2);

            int cabecalho = tabela.getStartCellReference().getRow();
            Cell valor = aba.getRow(cabecalho + 1).getCell(1);
            assertThat(valor.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(valor.getNumericCellValue()).isEqualTo(200.00);
            Cell vencimento = aba.getRow(cabecalho + 1).getCell(2);
            assertThat(DateUtil.isCellDateFormatted(vencimento)).isTrue();
            assertThat(mostrado(aba, cabecalho + 1, 2)).isEqualTo("10/08/2026");

            // Texto do usuario vai como texto: nao vira formula.
            Cell perigoso = aba.getRow(cabecalho + 2).getCell(0);
            assertThat(perigoso.getCellType()).isEqualTo(CellType.STRING);
            assertThat(perigoso.getStringCellValue()).isEqualTo("=HYPERLINK(\"http://mal\")");

            Row totais = aba.getRow(cabecalho - 2);
            List<String> textos = new ArrayList<>();
            totais.forEach(c -> textos.add(mostrado(aba, c.getRowIndex(), c.getColumnIndex())));
            assertThat(textos).contains("Linhas visíveis", "2", "Total");
            assertThat(totais.getCell(totais.getLastCellNum() - 1).getCellFormula())
                    .isEqualTo("SUBTOTAL(109,Cobrancas[Valor])");
            assertThat(totais.getCell(totais.getLastCellNum() - 1).getNumericCellValue()).isEqualTo(350.50);

            assertThat(aba.getDataValidations()).hasSize(1);
            assertThat(aba.getDataValidations().getFirst().getValidationConstraint().getExplicitListValues())
                    .containsExactly("Pendente", "Paga");
            assertThat(aba.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) (cabecalho + 1));
        }
    }

    @Test
    void abaSemLinhasAvisaEMantemATabela() throws IOException {
        try (XSSFWorkbook workbook = gerar(cobrancas(List.of()))) {
            XSSFSheet aba = workbook.getSheet("Cobranças");

            assertThat(aba.getTables()).hasSize(1);
            int cabecalho = aba.getTables().getFirst().getStartCellReference().getRow();
            assertThat(mostrado(aba, cabecalho - 2, 2)).isEqualTo("Nenhuma cobrança vence neste período.");
        }
    }
}
