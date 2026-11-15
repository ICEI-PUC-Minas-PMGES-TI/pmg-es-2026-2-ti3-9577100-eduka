package br.pucminas.eduka.backend.service.financeiro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;

import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Linha;
import br.pucminas.eduka.backend.service.privacidade.Formatos;

/**
 * Monta a planilha .xlsx do relatorio financeiro (IU19-A), com a cara da
 * escola: toda aba abre com o nome, o documento e o contato dela, o periodo e
 * quando foi gerada.
 *
 * <p>
 * A aba "Resumo" traz os numeros da tela (cartoes, receitas e despesas por
 * mes, formas de pagamento e os resumos por curso, tipo e mes). As outras, uma
 * por {@link SecaoExportada}, sao tabelas do Excel: filtro e ordenacao no
 * cabecalho, linhas novas herdam o formato, e o total acima da tabela usa
 * {@code SUBTOTAL}, que acompanha o filtro.
 * </p>
 *
 * <p>
 * E feita para ser editada: totais, saldos e percentuais sao formulas, entao
 * mudar um valor atualiza o resto. Valores e datas vao como numero e data, nao
 * como texto. Texto vai sempre como texto, entao nada vindo do usuario vira
 * formula (sem a injecao que o CSV precisa tratar).
 * </p>
 */
final class PlanilhaFinanceiraXlsx {

    /** Quem e a escola, ja formatado para gente. */
    record Escola(String nome, String detalhes) {
    }

    // Lavanda das telas e do PDF de dados do aluno.
    private static final String ROXO = "6A3FC4";
    private static final String VEU = "F5EEFE";
    private static final String BORDA = "E4D8F8";
    private static final String LISTRA = "FBF9FE";
    private static final String TINTA = "221A33";
    private static final String APAGADO = "5F5873";
    private static final String BRANCO = "FFFFFF";
    private static final String VERDE = "1E7F4F";
    private static final String AMBAR = "A15C00";
    private static final String VERMELHO = "B42318";

    private static final String FORMATO_MOEDA = "\"R$\" #,##0.00;[Red]-\"R$\" #,##0.00";
    private static final String FORMATO_DATA = "dd/mm/yyyy";
    private static final String FORMATO_PERCENTUAL = "0.0%";
    private static final String FONTE = "Calibri";

    /** Faixas da IU19 para a inadimplencia: ate 9% vai bem, de 20% em diante pede atencao. */
    private static final double INADIMPLENCIA_BAIXA = 0.09;
    private static final double INADIMPLENCIA_ALTA = 0.20;

    /** Linhas abaixo da tabela que ja vem com a lista suspensa, para quem acrescentar linhas. */
    private static final int LINHAS_EXTRAS_VALIDADAS = 500;

    /** Cabecalho da escola: marca, nome, detalhes, periodo e uma linha em branco. */
    private static final int LINHAS_DO_CABECALHO = 5;

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final Escola escola;
    private final String periodo;

    private final XSSFCellStyle marca;
    private final XSSFCellStyle nomeEscola;
    private final XSSFCellStyle detalhe;
    /** O que o numero ao lado quer dizer: recuado, para nao encostar no valor. */
    private final XSSFCellStyle explicacao;
    private final XSSFCellStyle tituloSecao;
    private final XSSFCellStyle tituloAba;
    private final XSSFCellStyle cabecalho;
    private final XSSFCellStyle cabecalhoNumero;
    private final XSSFCellStyle texto;
    private final XSSFCellStyle data;
    private final XSSFCellStyle moeda;
    private final XSSFCellStyle inteiro;
    private final XSSFCellStyle percentual;
    private final XSSFCellStyle rotuloForte;
    private final XSSFCellStyle moedaForte;
    private final XSSFCellStyle totalRotulo;
    private final XSSFCellStyle totalMoeda;
    private final XSSFCellStyle totalInteiro;
    private final XSSFCellStyle totalPercentual;
    private final XSSFCellStyle aviso;

    /**
     * @param periodo "Periodo: 01/08/2026 a 30/09/2026 · Gerado em ...", ja
     *                montado por quem chama
     */
    PlanilhaFinanceiraXlsx(Escola escola, String periodo) {
        this.escola = escola;
        this.periodo = periodo;

        marca = estilo(fonte(true, 9, ROXO));
        nomeEscola = estilo(fonte(true, 18, TINTA));
        detalhe = estilo(fonte(false, 10, APAGADO));
        explicacao = clonar(detalhe);
        explicacao.setIndention((short) 2);
        tituloAba = estilo(fonte(true, 12, ROXO));
        tituloSecao = clonar(tituloAba);
        tituloSecao.setBorderBottom(BorderStyle.MEDIUM);
        tituloSecao.setBottomBorderColor(cor(BORDA));

        cabecalho = estilo(fonte(true, 10, BRANCO));
        preencher(cabecalho, ROXO);
        cabecalho.setVerticalAlignment(VerticalAlignment.CENTER);
        cabecalhoNumero = clonar(cabecalho);
        cabecalhoNumero.setAlignment(HorizontalAlignment.RIGHT);

        XSSFFont normal = fonte(false, 10, TINTA);
        texto = celula(normal, null);
        data = celula(normal, FORMATO_DATA);
        data.setAlignment(HorizontalAlignment.LEFT);
        moeda = celula(normal, FORMATO_MOEDA);
        inteiro = celula(normal, "0");
        percentual = celula(normal, FORMATO_PERCENTUAL);

        XSSFFont forte = fonte(true, 10, TINTA);
        rotuloForte = celula(forte, null);
        moedaForte = celula(forte, FORMATO_MOEDA);

        totalRotulo = total(forte, null);
        totalMoeda = total(forte, FORMATO_MOEDA);
        totalInteiro = total(forte, "0");
        totalPercentual = total(forte, FORMATO_PERCENTUAL);

        aviso = estilo(fonte(false, 10, APAGADO));
        aviso.getFont().setItalic(true);

        var propriedades = workbook.getProperties();
        propriedades.getCoreProperties().setTitle("Relatório financeiro — " + escola.nome());
        propriedades.getCoreProperties().setCreator("Eduka");
        propriedades.getExtendedProperties().getUnderlyingProperties().setCompany(escola.nome());
    }

    // ---- Aba de resumo ----

    /**
     * A primeira aba, com os numeros da tela. {@code resumo} ja vem com os
     * grupos em texto para gente (tipo e mes por extenso).
     */
    void resumo(RelatorioFinanceiroResponse relatorio, RelatorioFinanceiroResponse.Resumo resumo,
            Function<RelatorioFinanceiroResponse.Forma, String> nomeDaForma) {
        XSSFSheet aba = workbook.createSheet("Resumo");
        aba.setDisplayGridlines(false);
        int[] larguras = { 34, 20, 20, 20, 16 };
        for (int i = 0; i < larguras.length; i++) {
            aba.setColumnWidth(i, larguras[i] * 256);
        }
        int linha = cabecalhoDaEscola(aba, larguras.length);

        // Numeros do periodo: rotulo, valor e o que o valor quer dizer.
        linha = tituloDaSecao(aba, linha, "Números do período", larguras.length);
        int recebido = linha;
        linha = numero(aba, linha, "Total recebido", relatorio.recebido().valor(),
                quantas(relatorio.recebido().quantidade(), "pagamento confirmado", "pagamentos confirmados")
                        + ", já sem os estornos");
        linha = numero(aba, linha, "Estornado", relatorio.estornado().valor(),
                quantas(relatorio.estornado().quantidade(), "estorno", "estornos"));
        linha = numero(aba, linha, "Despesas lançadas", relatorio.despesas().valor(),
                quantas(relatorio.despesas().quantidade(), "lançamento que vence", "lançamentos que vencem")
                        + " no período");
        int pagas = linha;
        linha = numero(aba, linha, "Despesas pagas", relatorio.despesasPagas().valor(),
                quantas(relatorio.despesasPagas().quantidade(), "despesa paga", "despesas pagas") + " no período");
        Row saldo = aba.createRow(linha++);
        celula(saldo, 0, "Saldo do período", rotuloForte);
        formula(saldo, 1, "B%d-B%d".formatted(recebido + 1, pagas + 1), moedaForte);
        celula(saldo, 2, "Total recebido menos as despesas pagas", explicacao);
        Row inadimplencia = aba.createRow(linha++);
        celula(inadimplencia, 0, "Inadimplência", rotuloForte);
        fracao(inadimplencia, 1, relatorio.inadimplencia().percentual(), percentual);
        celula(inadimplencia, 2, relatorio.inadimplencia().percentual() == null ? "Nada venceu no período"
                : Formatos.moeda(relatorio.inadimplencia().valor()) + " em "
                        + quantas(relatorio.inadimplencia().cobrancas(), "cobrança em atraso",
                                "cobranças em atraso"),
                explicacao);
        coresDaInadimplencia(aba, new CellRangeAddress(linha - 1, linha - 1, 1, 1));
        linha++;

        linha = receitasEDespesas(aba, linha, relatorio.receitasEDespesas());
        linha = formas(aba, linha, relatorio.formasPagamento(), nomeDaForma);
        linha = tabelaDeResumo(aba, linha, "Resumo por curso", "Curso", resumo.porCurso(), resumo.total());
        linha = tabelaDeResumo(aba, linha, "Resumo por tipo de cobrança", "Tipo", resumo.porTipo(), resumo.total());
        tabelaDeResumo(aba, linha, "Resumo por mês", "Mês", resumo.porMes(), resumo.total());

        configurarImpressao(aba, 0);
    }

    private int numero(XSSFSheet aba, int linha, String rotulo, BigDecimal valor, String significado) {
        Row row = aba.createRow(linha);
        celula(row, 0, rotulo, rotuloForte);
        celula(row, 1, valor, moedaForte);
        celula(row, 2, significado, explicacao);
        return linha + 1;
    }

    private int receitasEDespesas(XSSFSheet aba, int linha, List<RelatorioFinanceiroResponse.Mes> meses) {
        linha = tituloDaSecao(aba, linha, "Receitas e despesas por mês", 4);
        linha = cabecalhoDaTabela(aba, linha, "Mês", "Receitas", "Despesas pagas", "Saldo");
        int primeira = linha;
        for (RelatorioFinanceiroResponse.Mes mes : meses) {
            Row row = aba.createRow(linha);
            celula(row, 0, RelatorioFinanceiroService.mesPorExtenso(mes.mes()), texto);
            celula(row, 1, mes.receitas(), moeda);
            celula(row, 2, mes.despesas(), moeda);
            formula(row, 3, "B%1$d-C%1$d".formatted(linha + 1), moeda);
            linha++;
        }
        Row total = aba.createRow(linha);
        celula(total, 0, "Total do período", totalRotulo);
        for (int coluna = 1; coluna <= 3; coluna++) {
            formula(total, coluna, soma(coluna, primeira, linha - 1), totalMoeda);
        }
        listrar(aba, primeira, linha - 1, 0, 3);
        return linha + 2;
    }

    private int formas(XSSFSheet aba, int linha, List<RelatorioFinanceiroResponse.Forma> formas,
            Function<RelatorioFinanceiroResponse.Forma, String> nomeDaForma) {
        linha = tituloDaSecao(aba, linha, "Formas de pagamento", 4);
        linha = cabecalhoDaTabela(aba, linha, "Forma", "Valor", "Pagamentos", "% do recebido");
        int primeira = linha;
        int ultima = primeira + formas.size() - 1;
        for (RelatorioFinanceiroResponse.Forma forma : formas) {
            Row row = aba.createRow(linha);
            celula(row, 0, nomeDaForma.apply(forma), texto);
            celula(row, 1, forma.valor(), moeda);
            celula(row, 2, forma.quantidade(), inteiro);
            // Sobre o total da coluna, que e o recebido; vazio se ele nao for positivo.
            String totalDaColuna = "SUM($B$%d:$B$%d)".formatted(primeira + 1, ultima + 1);
            formula(row, 3, "IF(%1$s>0,B%2$d/%1$s,\"\")".formatted(totalDaColuna, linha + 1), percentual);
            linha++;
        }
        Row total = aba.createRow(linha);
        celula(total, 0, "Total recebido", totalRotulo);
        formula(total, 1, soma(1, primeira, ultima), totalMoeda);
        formula(total, 2, soma(2, primeira, ultima), totalInteiro);
        celula(total, 3, (String) null, totalPercentual);
        listrar(aba, primeira, ultima, 0, 3);
        return linha + 2;
    }

    private int tabelaDeResumo(XSSFSheet aba, int linha, String titulo, String coluna, List<Linha> linhas,
            Linha totalDoPeriodo) {
        linha = tituloDaSecao(aba, linha, titulo, 5);
        if (linhas.isEmpty()) {
            celula(aba.createRow(linha), 0, "Nenhuma cobrança vence neste período.", aviso);
            return linha + 2;
        }
        linha = cabecalhoDaTabela(aba, linha, coluna, "Cobrado", "Recebido", "Em aberto", "Inadimplência");
        int primeira = linha;
        for (Linha l : linhas) {
            Row row = aba.createRow(linha++);
            celula(row, 0, l.grupo(), texto);
            celula(row, 1, l.cobrado(), moeda);
            celula(row, 2, l.recebido(), moeda);
            celula(row, 3, l.emAberto(), moeda);
            fracao(row, 4, l.inadimplencia(), percentual);
        }
        Row total = aba.createRow(linha);
        celula(total, 0, "Total do período", totalRotulo);
        for (int c = 1; c <= 3; c++) {
            formula(total, c, soma(c, primeira, linha - 1), totalMoeda);
        }
        // A inadimplencia depende do que ja venceu, que a tabela nao traz: vai o valor da tela.
        fracao(total, 4, totalDoPeriodo.inadimplencia(), totalPercentual);
        listrar(aba, primeira, linha - 1, 0, 4);
        coresDaInadimplencia(aba, new CellRangeAddress(primeira, linha, 4, 4));
        return linha + 2;
    }

    // ---- Abas de linhas ----

    /** Uma aba com a secao como tabela do Excel, e os totais acima dela. */
    void aba(SecaoExportada secao) {
        XSSFSheet aba = workbook.createSheet(secao.titulo());
        aba.setDisplayGridlines(false);
        List<SecaoExportada.Coluna> colunas = secao.colunas();
        int linha = cabecalhoDaEscola(aba, colunas.size());

        // Totais (SUBTOTAL acompanha o filtro) e o titulo, numa linha so.
        int linhaDosTotais = linha;
        Row totais = aba.createRow(linha++);
        celula(totais, 0, secao.titulo(), tituloAba);
        linha++;

        int inicio = linha;
        Row cabecalhoRow = aba.createRow(linha++);
        cabecalhoRow.setHeightInPoints(22);
        for (int c = 0; c < colunas.size(); c++) {
            // Tudo a esquerda: a direita, a seta do filtro cobriria o titulo das colunas de valor.
            celula(cabecalhoRow, c, colunas.get(c).nome(), cabecalho);
        }
        for (Object[] valores : secao.linhas()) {
            Row row = aba.createRow(linha++);
            for (int c = 0; c < valores.length; c++) {
                valor(row, c, valores[c]);
            }
        }
        if (secao.linhas().isEmpty()) {
            // A tabela do Excel precisa de ao menos uma linha de dados.
            Row row = aba.createRow(linha++);
            for (int c = 0; c < colunas.size(); c++) {
                celula(row, c, (String) null, colunas.get(c).somar() ? moeda : texto);
            }
            celula(aba.getRow(linhaDosTotais), 2, secao.vazio(), aviso);
        }
        int fim = linha - 1;

        XSSFTable tabela = aba.createTable(new AreaReference(new CellReference(inicio, 0),
                new CellReference(fim, colunas.size() - 1), SpreadsheetVersion.EXCEL2007));
        tabela.setName(secao.tabela());
        tabela.setDisplayName(secao.tabela());
        tabela.getCTTable().addNewAutoFilter().setRef(tabela.getArea().formatAsString());
        CTTableStyleInfo estiloDaTabela = tabela.getCTTable().addNewTableStyleInfo();
        estiloDaTabela.setName("TableStyleLight1");
        // As listras vem da formatacao condicional, que segue a linha mesmo depois de ordenar.
        estiloDaTabela.setShowRowStripes(false);
        listrar(aba, inicio + 1, fim, 0, colunas.size() - 1);

        if (!secao.linhas().isEmpty()) {
            totais(totais, secao);
        }
        listas(aba, colunas, inicio + 1, fim + LINHAS_EXTRAS_VALIDADAS);
        larguras(aba, secao);
        aba.createFreezePane(0, inicio + 1);
        configurarImpressao(aba, inicio);
    }

    /** "12 linhas · Valor: R$ x", contando so o que o filtro deixa ver. */
    private void totais(Row row, SecaoExportada secao) {
        String primeira = secao.colunas().getFirst().nome();
        int c = Math.max(2, secao.colunas().size() - 4);
        celula(row, c++, "Linhas visíveis", rotuloForte);
        formula(row, c++, "SUBTOTAL(103,%s)".formatted(referencia(secao, primeira)), inteiro);
        for (SecaoExportada.Coluna coluna : secao.colunas()) {
            if (coluna.somar()) {
                celula(row, c++, "Total", rotuloForte);
                formula(row, c++, "SUBTOTAL(109,%s)".formatted(referencia(secao, coluna.nome())), moedaForte);
            }
        }
    }

    private static String referencia(SecaoExportada secao, String coluna) {
        return "%s[%s]".formatted(secao.tabela(), coluna);
    }

    /** Lista suspensa nas colunas de valores fixos, inclusive nas linhas que alguem acrescentar. */
    private static void listas(XSSFSheet aba, List<SecaoExportada.Coluna> colunas, int primeira, int ultima) {
        DataValidationHelper ajuda = aba.getDataValidationHelper();
        for (int c = 0; c < colunas.size(); c++) {
            List<String> opcoes = colunas.get(c).opcoes();
            if (opcoes.isEmpty()) {
                continue;
            }
            DataValidation validacao = ajuda.createValidation(
                    ajuda.createExplicitListConstraint(opcoes.toArray(String[]::new)),
                    new CellRangeAddressList(primeira, ultima, c, c));
            validacao.setShowErrorBox(true);
            validacao.createErrorBox("Valor fora da lista", "Escolha uma das opções da lista.");
            // No XSSF, "true" e o que mostra a setinha da lista.
            validacao.setSuppressDropDownArrow(true);
            aba.addValidationData(validacao);
        }
    }

    /** Pelo maior conteudo da coluna, com folga para o negrito e a seta do filtro; entre 12 e 50 caracteres. */
    private static void larguras(XSSFSheet aba, SecaoExportada secao) {
        for (int c = 0; c < secao.colunas().size(); c++) {
            int maior = secao.colunas().get(c).nome().length() + 4;
            for (Object[] valores : secao.linhas()) {
                maior = Math.max(maior, comprimento(valores[c]));
            }
            aba.setColumnWidth(c, Math.clamp(Math.round(maior * 1.15f) + 4, 12, 50) * 256);
        }
    }

    private static int comprimento(Object valor) {
        return switch (valor) {
            case null -> 0;
            case BigDecimal numero -> numero.toPlainString().length() + 6;
            case LocalDate ignorada -> 10;
            default -> valor.toString().length();
        };
    }

    // ---- Pecas comuns ----

    /** As linhas com a escola no topo da aba; devolve a primeira linha livre. */
    private int cabecalhoDaEscola(XSSFSheet aba, int colunas) {
        celula(aba.createRow(0), 0, "RELATÓRIO FINANCEIRO", marca);
        Row nome = aba.createRow(1);
        nome.setHeightInPoints(26);
        celula(nome, 0, escola.nome(), nomeEscola);
        if (escola.detalhes() != null) {
            celula(aba.createRow(2), 0, escola.detalhes(), detalhe);
        }
        celula(aba.createRow(3), 0, periodo, detalhe);
        for (int linha = 1; linha <= 3; linha++) {
            // Mescladas na largura das colunas: o texto nao some atras da coluna B ao editar.
            if (aba.getRow(linha) != null && colunas > 1) {
                aba.addMergedRegion(new CellRangeAddress(linha, linha, 0, colunas - 1));
            }
        }
        return LINHAS_DO_CABECALHO;
    }

    private int tituloDaSecao(XSSFSheet aba, int linha, String titulo, int colunas) {
        Row row = aba.createRow(linha);
        row.setHeightInPoints(20);
        for (int c = 0; c < colunas; c++) {
            celula(row, c, c == 0 ? titulo : null, tituloSecao);
        }
        return linha + 1;
    }

    private int cabecalhoDaTabela(XSSFSheet aba, int linha, String... nomes) {
        Row row = aba.createRow(linha);
        row.setHeightInPoints(20);
        for (int c = 0; c < nomes.length; c++) {
            celula(row, c, nomes[c], c == 0 ? cabecalho : cabecalhoNumero);
        }
        return linha + 1;
    }

    /** Listra as linhas pares, por formula: continua certo depois de ordenar ou inserir linhas. */
    private void listrar(XSSFSheet aba, int primeira, int ultima, int deColuna, int ateColuna) {
        if (ultima < primeira) {
            return;
        }
        SheetConditionalFormatting formatacao = aba.getSheetConditionalFormatting();
        ConditionalFormattingRule regra = formatacao.createConditionalFormattingRule("MOD(ROW(),2)=0");
        regra.createPatternFormatting().setFillBackgroundColor(cor(LISTRA));
        formatacao.addConditionalFormatting(
                new CellRangeAddress[] { new CellRangeAddress(primeira, ultima, deColuna, ateColuna) }, regra);
    }

    /** Verde, ambar ou vermelho, como o selo da tela. */
    private void coresDaInadimplencia(XSSFSheet aba, CellRangeAddress celulas) {
        SheetConditionalFormatting formatacao = aba.getSheetConditionalFormatting();
        ConditionalFormattingRule alta = formatacao.createConditionalFormattingRule(ComparisonOperator.GE,
                String.valueOf(INADIMPLENCIA_ALTA));
        alta.createFontFormatting().setFontColor(cor(VERMELHO));
        ConditionalFormattingRule media = formatacao.createConditionalFormattingRule(ComparisonOperator.GE,
                String.valueOf(INADIMPLENCIA_BAIXA));
        media.createFontFormatting().setFontColor(cor(AMBAR));
        ConditionalFormattingRule baixa = formatacao.createConditionalFormattingRule(ComparisonOperator.LT,
                String.valueOf(INADIMPLENCIA_BAIXA));
        baixa.createFontFormatting().setFontColor(cor(VERDE));
        formatacao.addConditionalFormatting(new CellRangeAddress[] { celulas },
                new ConditionalFormattingRule[] { alta, media, baixa });
    }

    /** Paisagem, uma pagina de largura, a escola no topo e "Pagina X de Y" no rodape. */
    private void configurarImpressao(XSSFSheet aba, int linhaDoCabecalho) {
        aba.getPrintSetup().setLandscape(true);
        aba.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        aba.setFitToPage(true);
        aba.getPrintSetup().setFitWidth((short) 1);
        aba.getPrintSetup().setFitHeight((short) 0);
        aba.setHorizontallyCenter(true);
        if (linhaDoCabecalho > 0) {
            aba.setRepeatingRows(CellRangeAddress.valueOf("%1$d:%1$d".formatted(linhaDoCabecalho + 1)));
        }
        aba.getHeader().setLeft(escola.nome().replace("&", "&&"));
        aba.getHeader().setRight("&A");
        aba.getFooter().setCenter("Página &P de &N");
    }

    private void valor(Row row, int coluna, Object valor) {
        switch (valor) {
            case BigDecimal numero -> celula(row, coluna, numero, moeda);
            case LocalDate dia -> {
                Cell cell = row.createCell(coluna);
                cell.setCellValue(dia);
                cell.setCellStyle(data);
            }
            case null -> celula(row, coluna, (String) null, texto);
            default -> celula(row, coluna, valor.toString(), texto);
        }
    }

    private static void celula(Row row, int coluna, String valor, XSSFCellStyle estilo) {
        Cell cell = row.createCell(coluna);
        if (valor != null) {
            cell.setCellValue(valor);
        }
        cell.setCellStyle(estilo);
    }

    private static void celula(Row row, int coluna, BigDecimal valor, XSSFCellStyle estilo) {
        Cell cell = row.createCell(coluna);
        if (valor != null) {
            cell.setCellValue(valor.doubleValue());
        }
        cell.setCellStyle(estilo);
    }

    private static void celula(Row row, int coluna, long valor, XSSFCellStyle estilo) {
        Cell cell = row.createCell(coluna);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    /** O backend manda o percentual de 0 a 100; a planilha guarda a fracao, com formato %. */
    private static void fracao(Row row, int coluna, BigDecimal percentual, XSSFCellStyle estilo) {
        Cell cell = row.createCell(coluna);
        if (percentual != null) {
            cell.setCellValue(percentual.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP).doubleValue());
        }
        cell.setCellStyle(estilo);
    }

    private static void formula(Row row, int coluna, String formula, XSSFCellStyle estilo) {
        Cell cell = row.createCell(coluna);
        cell.setCellFormula(formula);
        cell.setCellStyle(estilo);
    }

    private static String soma(int coluna, int primeira, int ultima) {
        if (ultima < primeira) {
            return "0";
        }
        String letra = CellReference.convertNumToColString(coluna);
        return "SUM(%1$s%2$d:%1$s%3$d)".formatted(letra, primeira + 1, ultima + 1);
    }

    private static String quantas(long quantidade, String singular, String plural) {
        return quantidade + " " + (quantidade == 1 ? singular : plural);
    }

    // ---- Estilos ----

    private XSSFFont fonte(boolean negrito, double tamanho, String cor) {
        XSSFFont fonte = workbook.createFont();
        fonte.setFontName(FONTE);
        fonte.setBold(negrito);
        fonte.setFontHeight(tamanho);
        fonte.setColor(cor(cor));
        return fonte;
    }

    private XSSFCellStyle estilo(XSSFFont fonte) {
        XSSFCellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fonte);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        return estilo;
    }

    /** Celula de tabela: borda fina lavanda embaixo. */
    private XSSFCellStyle celula(XSSFFont fonte, String formato) {
        XSSFCellStyle estilo = estilo(fonte);
        if (formato != null) {
            estilo.setDataFormat(workbook.createDataFormat().getFormat(formato));
        }
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBottomBorderColor(cor(BORDA));
        return estilo;
    }

    /** Linha de total: fundo veu e borda roxa em cima. */
    private XSSFCellStyle total(XSSFFont fonte, String formato) {
        XSSFCellStyle estilo = celula(fonte, formato);
        preencher(estilo, VEU);
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setTopBorderColor(cor(ROXO));
        return estilo;
    }

    private XSSFCellStyle clonar(XSSFCellStyle original) {
        XSSFCellStyle copia = workbook.createCellStyle();
        copia.cloneStyleFrom(original);
        return copia;
    }

    private static void preencher(XSSFCellStyle estilo, String cor) {
        estilo.setFillForegroundColor(cor(cor));
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    /**
     * Opaca, em ARGB: com so RGB (6 digitos) o Excel le o preenchimento como
     * transparente, e o cabecalho branco sobre roxo some.
     */
    private static XSSFColor cor(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        return new XSSFColor(new byte[] { (byte) 0xFF, (byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb }, null);
    }

    byte[] paraBytes() {
        workbook.setActiveSheet(0);
        // O Excel recalcula ao abrir: as formulas vao sem valor guardado.
        workbook.setForceFormulaRecalculation(true);
        try (workbook; ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            workbook.write(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel gerar a planilha", e);
        }
    }
}
