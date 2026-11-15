package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.financeiro.TipoCobranca;
import br.pucminas.eduka.backend.dto.comum.ArquivoCsv;
import br.pucminas.eduka.backend.dto.comum.ArquivoPlanilha;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse;
import br.pucminas.eduka.backend.dto.financeiro.CobrancaRequest;
import br.pucminas.eduka.backend.dto.financeiro.HistoricoFinanceiroResponse;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Forma;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Linha;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Mes;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.financeiro.CobrancaService;
import br.pucminas.eduka.backend.service.financeiro.DashboardFinanceiroService;
import br.pucminas.eduka.backend.service.financeiro.RelatorioFinanceiroService;

/**
 * As contas dos relatorios sobre dados gravados no Postgres: cada numero com a
 * sua data, os limites do periodo no fuso da escola, os agrupamentos e o
 * isolamento entre escolas.
 */
@AutoConfigureMockMvc
class RelatorioFinanceiroBancoRealTest extends BancoRealTest {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");
    /** Periodo no passado: todo vencimento dentro dele ja venceu. */
    private static final LocalDate DE = LocalDate.of(2025, 3, 5);
    private static final LocalDate ATE = LocalDate.of(2025, 5, 31);
    private static final Set<ArquivoCsv.Conjunto> TODOS = EnumSet.allOf(ArquivoCsv.Conjunto.class);

    @Autowired
    private RelatorioFinanceiroService relatorioFinanceiroService;

    @Autowired
    private CobrancaService cobrancaService;

    @Autowired
    private DashboardFinanceiroService dashboardFinanceiroService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private static BigDecimal reais(String valor) {
        return new BigDecimal(valor);
    }

    /** Esse horario de {@code dia} no fuso da escola. */
    private static Instant em(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto).atZone(FUSO_ESCOLA).toInstant();
    }

    /**
     * Os cenarios de um periodo inteiro. Os comentarios dao o que cada cobranca
     * soma: as contas dos testes partem daqui.
     */
    private UUID escolaComMovimento() {
        UUID escola = dados.escola();
        UUID ana = dados.aluno(escola, "Ana Souza");
        UUID bruno = dados.aluno(escola, "Bruno Lima");
        UUID carla = dados.aluno(escola, "Carla Dias");
        UUID matriculaDaAna = dados.matriculaNoCurso(escola, ana, "Ensino Medio");
        UUID matriculaDoBruno = dados.matriculaNoCurso(escola, bruno, "Curso de Ingles");

        // Paga online: vale o pago no provedor (195), nao o cobrado (200).
        UUID online = dados.cobrancaPaga(escola, ana, reais("200.00"), LocalDate.of(2025, 3, 10),
                em(LocalDate.of(2025, 3, 9), 10, 0));
        dados.pagamentoOnline(escola, online, reais("195.00"), FormaPagamento.PIX);
        dados.classificar(online, matriculaDaAna, TipoCobranca.MENSALIDADE);
        // Baixa manual, sem forma.
        UUID manual = dados.cobrancaPaga(escola, bruno, reais("100.00"), LocalDate.of(2025, 4, 11),
                em(LocalDate.of(2025, 4, 12), 10, 0));
        dados.classificar(manual, matriculaDoBruno, TipoCobranca.MATERIAL);
        // Vencida e nao paga, ainda gravada como PENDENTE: a rotina nao rodou.
        UUID semRotina = dados.cobranca(escola, ana, StatusCobranca.PENDENTE, LocalDate.of(2025, 4, 10),
                reais("300.00"));
        dados.classificar(semRotina, matriculaDaAna, TipoCobranca.MENSALIDADE);
        // Avulsa, sem matricula nem tipo.
        dados.cobranca(escola, carla, StatusCobranca.EM_ATRASO, LocalDate.of(2025, 5, 20), reais("50.00"));

        // Vence antes do periodo e foi paga nele: so no recebido.
        UUID pagaAntiga = dados.cobrancaPaga(escola, carla, reais("40.00"), LocalDate.of(2025, 2, 10),
                em(LocalDate.of(2025, 3, 6), 10, 0));
        dados.pagamentoOnline(escola, pagaAntiga, reais("40.00"), FormaPagamento.BOLETO);
        // Vence no periodo e foi paga depois dele: so no resumo.
        dados.cobrancaPaga(escola, carla, reais("70.00"), LocalDate.of(2025, 5, 30),
                em(LocalDate.of(2025, 6, 2), 10, 0));
        // Fora de tudo: cancelada, e paga um minuto antes do primeiro dia.
        dados.cobranca(escola, ana, StatusCobranca.CANCELADA, LocalDate.of(2025, 4, 1), reais("999.00"));
        dados.cobrancaPaga(escola, ana, reais("1.00"), LocalDate.of(2025, 1, 1), em(DE.minusDays(1), 23, 59));

        UUID categoria = dados.categoriaDespesa(escola, "Contas");
        dados.despesa(escola, categoria, reais("30.00"), LocalDate.of(2025, 3, 15), SituacaoDespesa.PAGA,
                em(LocalDate.of(2025, 3, 15), 9, 0));
        dados.despesa(escola, categoria, reais("70.00"), LocalDate.of(2025, 4, 15), SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, categoria, reais("500.00"), LocalDate.of(2025, 4, 20), SituacaoDespesa.CANCELADA, null);
        dados.despesa(escola, categoria, reais("11.00"), ATE.plusDays(1), SituacaoDespesa.A_PAGAR, null);

        // Outra escola, com de tudo um pouco: nada disso pode aparecer.
        UUID outra = dados.escola();
        UUID alunoDeOutra = dados.aluno(outra, "Zeca de Outra Escola");
        dados.cobrancaPaga(outra, alunoDeOutra, reais("7000.00"), LocalDate.of(2025, 4, 10),
                em(LocalDate.of(2025, 4, 10), 12, 0));
        dados.cobranca(outra, alunoDeOutra, StatusCobranca.PENDENTE, LocalDate.of(2025, 4, 10), reais("8000.00"));
        dados.despesa(outra, dados.categoriaDespesa(outra), reais("9000.00"), LocalDate.of(2025, 4, 10),
                SituacaoDespesa.A_PAGAR, null);
        return escola;
    }

    @Test
    void cardsContamCadaNumeroPelaSuaDataESoDaEscola() {
        autenticarComo("FINANCEIRO", escolaComMovimento());

        RelatorioFinanceiroResponse relatorio = relatorioFinanceiroService.gerar(DE, ATE);

        assertThat(relatorio.de()).isEqualTo(DE);
        assertThat(relatorio.ate()).isEqualTo(ATE);
        // Pagos no periodo: 195 + 100 + 40.
        assertThat(relatorio.recebido().valor()).isEqualByComparingTo("335.00");
        assertThat(relatorio.recebido().quantidade()).isEqualTo(3);
        assertThat(relatorio.estornado().valor()).isZero();
        // Lancadas, pelo vencimento: 30 + 70; a cancelada e a do dia seguinte ficam de fora.
        assertThat(relatorio.despesas().valor()).isEqualByComparingTo("100.00");
        assertThat(relatorio.despesas().quantidade()).isEqualTo(2);
        // So os 30 ja sairam da conta: e o que o saldo desconta.
        assertThat(relatorio.despesasPagas().valor()).isEqualByComparingTo("30.00");
        assertThat(relatorio.despesasPagas().quantidade()).isEqualTo(1);
        assertThat(relatorio.saldo()).isEqualByComparingTo("305.00");
        // Vencidas e nao pagas: 300 + 50, sobre 720 que venceu (350 + 200 + 100 + 70).
        assertThat(relatorio.inadimplencia().valor()).isEqualByComparingTo("350.00");
        assertThat(relatorio.inadimplencia().cobrancas()).isEqualTo(2);
        assertThat(relatorio.inadimplencia().percentual()).isEqualByComparingTo("48.6");
    }

    @Test
    void receitasEDespesasTrazemTodoMesDoPeriodoComOSaldo() {
        autenticarComo("FINANCEIRO", escolaComMovimento());

        List<Mes> meses = relatorioFinanceiroService.gerar(DE, ATE).receitasEDespesas();

        assertThat(meses).extracting(Mes::mes)
                .containsExactly(YearMonth.of(2025, 3), YearMonth.of(2025, 4), YearMonth.of(2025, 5));
        assertThat(meses.get(0).receitas()).isEqualByComparingTo("235.00");
        assertThat(meses.get(0).despesas()).isEqualByComparingTo("30.00");
        assertThat(meses.get(0).saldo()).isEqualByComparingTo("205.00");
        assertThat(meses.get(1).receitas()).isEqualByComparingTo("100.00");
        // Os 70 de abril estao lancados, mas ainda nao foram pagos.
        assertThat(meses.get(1).despesas()).isZero();
        assertThat(meses.get(1).saldo()).isEqualByComparingTo("100.00");
        // Sem movimento, mas na lista: o grafico mostra o mes vazio.
        assertThat(meses.get(2).receitas()).isZero();
        assertThat(meses.get(2).despesas()).isZero();
        assertThat(meses.get(2).saldo()).isZero();
    }

    @Test
    void receitasEDespesasBatemComOGraficoDoInicio() {
        autenticarComo("FINANCEIRO", escolaComMovimento());

        List<Mes> relatorio = relatorioFinanceiroService.gerar(DE, ATE).receitasEDespesas();
        List<DashboardFinanceiroResponse.Mes> inicio = dashboardFinanceiroService.movimento(DE, ATE);

        assertThat(relatorio).hasSameSizeAs(inicio);
        for (int i = 0; i < relatorio.size(); i++) {
            assertThat(relatorio.get(i).mes()).isEqualTo(inicio.get(i).mes());
            assertThat(relatorio.get(i).receitas()).isEqualByComparingTo(inicio.get(i).entrou());
            assertThat(relatorio.get(i).despesas()).isEqualByComparingTo(inicio.get(i).saiu());
        }
    }

    @Test
    void estornoSaiDoRecebidoNoPeriodoDoEstornoEACobrancaContinuaEncerrada() {
        UUID escola = dados.escola();
        UUID ana = dados.aluno(escola, "Ana Souza");
        UUID matricula = dados.matriculaNoCurso(escola, ana, "Ensino Medio");
        UUID paga = dados.cobrancaPaga(escola, ana, reais("200.00"), LocalDate.of(2025, 3, 10),
                em(LocalDate.of(2025, 3, 9), 10, 0));
        dados.pagamentoOnline(escola, paga, reais("200.00"), FormaPagamento.PIX);
        dados.classificar(paga, matricula, TipoCobranca.MENSALIDADE);
        // Dois parciais: um em abril, dentro do periodo seguinte, e um em marco.
        dados.estorno(escola, paga, reais("30.00"), em(LocalDate.of(2025, 3, 20), 0, 0));
        dados.estorno(escola, paga, reais("50.00"), em(LocalDate.of(2025, 4, 5), 0, 0));

        autenticarComo("FINANCEIRO", escola);
        RelatorioFinanceiroResponse marco = relatorioFinanceiroService.gerar(LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31));
        RelatorioFinanceiroResponse abril = relatorioFinanceiroService.gerar(LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30));

        // Em marco entrou 200 e saiu 30.
        assertThat(marco.recebido().valor()).isEqualByComparingTo("170.00");
        assertThat(marco.recebido().quantidade()).isEqualTo(1);
        assertThat(marco.estornado().valor()).isEqualByComparingTo("30.00");
        assertThat(marco.estornado().quantidade()).isEqualTo(1);
        assertThat(marco.saldo()).isEqualByComparingTo("170.00");
        assertThat(marco.receitasEDespesas().getFirst().receitas()).isEqualByComparingTo("170.00");
        assertThat(marco.formasPagamento().getFirst().valor()).isEqualByComparingTo("170.00");
        assertThat(marco.formasPagamento().getFirst().quantidade()).isEqualTo(1);
        assertThat(marco.formasPagamento().getFirst().percentual()).isEqualByComparingTo("100.0");
        // O resumo olha a cobranca: o que ficou dela ate hoje, sem voltar para em aberto.
        confere(marco.resumo().total(), null, "200.00", "120.00", "0", "0");
        assertThat(marco.inadimplencia().valor()).isZero();

        // Em abril so saiu dinheiro, de um pagamento de antes: o recebido fica negativo.
        assertThat(abril.recebido().valor()).isEqualByComparingTo("-50.00");
        assertThat(abril.recebido().quantidade()).isZero();
        assertThat(abril.estornado().valor()).isEqualByComparingTo("50.00");
        assertThat(abril.saldo()).isEqualByComparingTo("-50.00");
        assertThat(abril.formasPagamento().getFirst().valor()).isEqualByComparingTo("-50.00");
        assertThat(abril.formasPagamento().getFirst().quantidade()).isZero();
        assertThat(abril.formasPagamento().getFirst().percentual()).isNull();
        assertThat(abril.resumo().porCurso()).isEmpty();

        // O grafico do Inicio desconta igual.
        assertThat(dashboardFinanceiroService.movimento(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 4, 30)))
                .extracting(DashboardFinanceiroResponse.Mes::entrou)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(reais("170.00"), reais("-50.00"));

        // O historico do aluno mostra o pago e o que voltou, e o total ja vem sem o estornado.
        HistoricoFinanceiroResponse historico = cobrancaService.historicoDoAluno(ana);
        assertThat(historico.resumo().valorPago()).isEqualByComparingTo("120.00");
        assertThat(historico.cobrancas().getFirst().pagamento().valorEstornado()).isEqualByComparingTo("80.00");
        assertThat(historico.cobrancas().getFirst().status()).isEqualTo(StatusCobranca.PAGA);

        String csv = csv(relatorioFinanceiroService.exportar(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31),
                Set.of(ArquivoCsv.Conjunto.PAGAMENTOS), ArquivoCsv.Separador.PONTO_E_VIRGULA));
        assertThat(csv.lines()).containsExactly(
                "Pagamentos",
                "Data;Aluno;Descrição;Valor;Forma;Movimento",
                "09/03/2025;Ana Souza;Mensalidade;200,00;PIX;Pagamento",
                "20/03/2025;Ana Souza;Mensalidade;-30,00;PIX;Estorno");
    }

    @Test
    void formasDePagamentoSomamORecebidoComABaixaManualAParte() {
        autenticarComo("FINANCEIRO", escolaComMovimento());

        List<Forma> formas = relatorioFinanceiroService.gerar(DE, ATE).formasPagamento();

        assertThat(formas).extracting(Forma::forma).containsExactly(FormaPagamento.PIX, FormaPagamento.BOLETO, null);
        assertThat(formas.get(0).valor()).isEqualByComparingTo("195.00");
        assertThat(formas.get(0).quantidade()).isEqualTo(1);
        assertThat(formas.get(0).percentual()).isEqualByComparingTo("58.2");
        assertThat(formas.get(1).valor()).isEqualByComparingTo("40.00");
        assertThat(formas.get(1).percentual()).isEqualByComparingTo("11.9");
        assertThat(formas.get(2).valor()).isEqualByComparingTo("100.00");
        assertThat(formas.get(2).percentual()).isEqualByComparingTo("29.9");
    }

    private static void confere(Linha linha, String grupo, String cobrado, String recebido, String emAberto,
            String inadimplencia) {
        assertThat(linha.grupo()).isEqualTo(grupo);
        assertThat(linha.cobrado()).isEqualByComparingTo(cobrado);
        assertThat(linha.recebido()).isEqualByComparingTo(recebido);
        assertThat(linha.emAberto()).isEqualByComparingTo(emAberto);
        assertThat(linha.inadimplencia()).isEqualByComparingTo(inadimplencia);
    }

    @Test
    void resumoAgrupaPorCursoDoMaiorCobradoAoMenor() {
        autenticarComo("FINANCEIRO", escolaComMovimento());

        List<Linha> linhas = relatorioFinanceiroService.gerar(DE, ATE).resumo().porCurso();

        assertThat(linhas).hasSize(3);
        confere(linhas.get(0), "Ensino Medio", "500.00", "195.00", "300.00", "60.0");
        confere(linhas.get(1), "Sem curso", "120.00", "70.00", "50.00", "41.7");
        confere(linhas.get(2), "Curso de Ingles", "100.00", "100.00", "0", "0");
    }

    @Test
    void resumoAgrupaPorTipoEPorMesDoVencimentoComOMesmoTotal() {
        autenticarComo("FINANCEIRO", escolaComMovimento());

        RelatorioFinanceiroResponse.Resumo resumo = relatorioFinanceiroService.gerar(DE, ATE).resumo();

        assertThat(resumo.porTipo()).hasSize(3);
        confere(resumo.porTipo().get(0), "MENSALIDADE", "500.00", "195.00", "300.00", "60.0");
        confere(resumo.porTipo().get(1), "MATERIAL", "100.00", "100.00", "0", "0");
        confere(resumo.porTipo().get(2), "OUTRO", "120.00", "70.00", "50.00", "41.7");

        assertThat(resumo.porMes()).hasSize(3);
        confere(resumo.porMes().get(0), "2025-03", "200.00", "195.00", "0", "0");
        confere(resumo.porMes().get(1), "2025-04", "400.00", "100.00", "300.00", "75.0");
        confere(resumo.porMes().get(2), "2025-05", "120.00", "70.00", "50.00", "41.7");

        confere(resumo.total(), null, "720.00", "365.00", "350.00", "48.6");
    }

    @Test
    void cobrancaQueAindaNaoVenceuFicaEmAbertoSemSerInadimplencia() {
        UUID escola = dados.escola();
        LocalDate hoje = LocalDate.now(FUSO_ESCOLA);
        dados.cobranca(escola, dados.aluno(escola), StatusCobranca.PENDENTE, hoje.plusDays(5), reais("100.00"));

        autenticarComo("FINANCEIRO", escola);
        RelatorioFinanceiroResponse relatorio = relatorioFinanceiroService.gerar(hoje, hoje.plusDays(10));

        assertThat(relatorio.inadimplencia().valor()).isZero();
        assertThat(relatorio.inadimplencia().percentual()).isNull();
        assertThat(relatorio.resumo().total().emAberto()).isEqualByComparingTo("100.00");
        assertThat(relatorio.resumo().total().inadimplencia()).isNull();
    }

    @Test
    void periodoSemMovimentoVemZeradoComAsDuasFormas() {
        autenticarComo("MASTER", dados.escola());

        RelatorioFinanceiroResponse relatorio = relatorioFinanceiroService.gerar(DE, ATE);

        assertThat(relatorio.recebido().valor()).isZero();
        assertThat(relatorio.recebido().quantidade()).isZero();
        assertThat(relatorio.despesas().valor()).isZero();
        assertThat(relatorio.saldo()).isZero();
        assertThat(relatorio.inadimplencia().percentual()).isNull();
        assertThat(relatorio.receitasEDespesas()).hasSize(3);
        assertThat(relatorio.formasPagamento()).extracting(Forma::forma)
                .containsExactly(FormaPagamento.PIX, FormaPagamento.BOLETO);
        assertThat(relatorio.formasPagamento()).allSatisfy(f -> assertThat(f.percentual()).isNull());
        assertThat(relatorio.resumo().porCurso()).isEmpty();
        assertThat(relatorio.resumo().porTipo()).isEmpty();
        assertThat(relatorio.resumo().porMes()).isEmpty();
        assertThat(relatorio.resumo().total().cobrado()).isZero();
    }

    @Test
    void recusaPeriodoInvertidoOuMaiorQue24Meses() {
        autenticarComo("FINANCEIRO", dados.escola());
        LocalDate janeiro = LocalDate.of(2025, 1, 1);

        assertThatThrownBy(() -> relatorioFinanceiroService.gerar(janeiro, janeiro.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> relatorioFinanceiroService.gerar(janeiro, LocalDate.of(2027, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> relatorioFinanceiroService.exportar(janeiro, LocalDate.of(2027, 1, 1), TODOS,
                ArquivoCsv.Separador.VIRGULA)).isInstanceOf(IllegalArgumentException.class);
        assertThat(relatorioFinanceiroService.gerar(janeiro, LocalDate.of(2026, 12, 31)).receitasEDespesas())
                .hasSize(24);
    }

    private String csv(ArquivoCsv arquivo) {
        String texto = new String(arquivo.conteudo(), StandardCharsets.UTF_8);
        assertThat(texto).startsWith("﻿");
        return texto.substring(1);
    }

    @Test
    void exportarTrazAsTresSecoesESomaOMesmoQueATela() {
        autenticarComo("FINANCEIRO", escolaComMovimento());

        ArquivoCsv arquivo = relatorioFinanceiroService.exportar(DE, ATE, TODOS,
                ArquivoCsv.Separador.PONTO_E_VIRGULA);

        assertThat(arquivo.nome()).isEqualTo("relatorio-financeiro-2025-03-05-a-2025-05-31.csv");
        // Nada da outra escola, da cancelada nem do que caiu fora do periodo.
        assertThat(csv(arquivo).split("\r\n")).containsExactly(
                "Cobranças",
                "Aluno;Curso;Tipo;Descrição;Valor;Vencimento;Situação;Pago em",
                "Ana Souza;Ensino Medio;Mensalidade;Mensalidade;200,00;10/03/2025;Paga;09/03/2025",
                "Ana Souza;Ensino Medio;Mensalidade;Mensalidade;300,00;10/04/2025;Em atraso;",
                "Bruno Lima;Curso de Ingles;Material;Mensalidade;100,00;11/04/2025;Paga;12/04/2025",
                "Carla Dias;Sem curso;Outro;Mensalidade;50,00;20/05/2025;Em atraso;",
                "Carla Dias;Sem curso;Outro;Mensalidade;70,00;30/05/2025;Paga;02/06/2025",
                "",
                "Pagamentos",
                "Data;Aluno;Descrição;Valor;Forma;Movimento",
                "06/03/2025;Carla Dias;Mensalidade;40,00;Boleto;Pagamento",
                "09/03/2025;Ana Souza;Mensalidade;195,00;PIX;Pagamento",
                "12/04/2025;Bruno Lima;Mensalidade;100,00;Baixa manual;Pagamento",
                "",
                "Despesas",
                "Vencimento;Categoria;Descrição;Valor;Situação;Paga em",
                "15/03/2025;Contas;Conta de luz;30,00;Paga;15/03/2025",
                "15/04/2025;Contas;Conta de luz;70,00;A pagar;");
    }

    @Test
    void exportarTrazSoOsConjuntosPedidosComOSeparadorEscolhido() {
        autenticarComo("MASTER", escolaComMovimento());

        String texto = csv(relatorioFinanceiroService.exportar(DE, ATE, Set.of(ArquivoCsv.Conjunto.PAGAMENTOS),
                ArquivoCsv.Separador.VIRGULA));

        assertThat(texto.lines()).containsExactly(
                "Pagamentos",
                "Data,Aluno,Descrição,Valor,Forma,Movimento",
                "06/03/2025,Carla Dias,Mensalidade,40.00,Boleto,Pagamento",
                "09/03/2025,Ana Souza,Mensalidade,195.00,PIX,Pagamento",
                "12/04/2025,Bruno Lima,Mensalidade,100.00,Baixa manual,Pagamento");
    }

    @Test
    void exportarSemConjuntoERecusadoEPeriodoVazioTrazSoOsCabecalhos() {
        autenticarComo("FINANCEIRO", dados.escola());

        assertThatThrownBy(() -> relatorioFinanceiroService.exportar(DE, ATE, Set.of(),
                ArquivoCsv.Separador.PONTO_E_VIRGULA)).isInstanceOf(IllegalArgumentException.class);

        assertThat(csv(relatorioFinanceiroService.exportar(DE, ATE, TODOS, ArquivoCsv.Separador.PONTO_E_VIRGULA))
                .lines().filter(l -> !l.isEmpty()).count()).isEqualTo(6);
    }

    @Test
    void planilhaTemAEscolaOResumoDaTelaEAsMesmasLinhasDoCsv() throws Exception {
        UUID escola = escolaComMovimento();
        jdbc.update("UPDATE ESCOLA SET NOME = 'Colégio São José', CNPJ_CPF = '12345678000190', CIDADE = 'Belo "
                + "Horizonte', ESTADO = 'MG' WHERE ID_ESCOLA = ?", escola);
        autenticarComo("FINANCEIRO", escola);

        ArquivoPlanilha arquivo = relatorioFinanceiroService.exportarPlanilha(DE, ATE);

        assertThat(arquivo.nome()).isEqualTo("relatorio-financeiro-colegio-sao-jose-2025-03-05-a-2025-05-31.xlsx");
        try (XSSFWorkbook planilha = new XSSFWorkbook(new ByteArrayInputStream(arquivo.conteudo()))) {
            XSSFFormulaEvaluator.evaluateAllFormulaCells(planilha);
            assertThat(planilha.sheetIterator()).toIterable().extracting(s -> s.getSheetName())
                    .containsExactly("Resumo", "Cobranças", "Pagamentos", "Despesas");
            XSSFSheet resumo = planilha.getSheet("Resumo");
            assertThat(resumo.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Colégio São José");
            assertThat(resumo.getRow(2).getCell(0).getStringCellValue())
                    .isEqualTo("CNPJ 12.345.678/0001-90  ·  Belo Horizonte/MG");
            assertThat(resumo.getRow(3).getCell(0).getStringCellValue()).startsWith("Período: 05/03/2025 a 31/05/2025");
            // Os numeros da tela: 335 recebidos menos 30 de despesas pagas.
            assertThat(valorAoLadoDe(resumo, "Total recebido")).isEqualTo(335.00);
            assertThat(valorAoLadoDe(resumo, "Saldo do período")).isEqualTo(305.00);
            assertThat(valorAoLadoDe(resumo, "Inadimplência")).isEqualTo(0.486);
            assertThat(valorAoLadoDe(resumo, "Mensalidade")).isEqualTo(500.00);

            // As linhas do CSV, sem a cobranca cancelada nem nada da outra escola.
            XSSFSheet cobrancas = planilha.getSheet("Cobranças");
            assertThat(cobrancas.getTables().getFirst().getRowCount() - 1).isEqualTo(5);
            assertThat(textos(cobrancas)).contains("Ana Souza", "Ensino Medio", "Em atraso")
                    .doesNotContain("Zeca de Outra Escola", "Cancelada");
            XSSFSheet pagamentos = planilha.getSheet("Pagamentos");
            assertThat(totalDaAba(pagamentos)).isEqualTo(335.00);
            assertThat(totalDaAba(planilha.getSheet("Despesas"))).isEqualTo(100.00);
        }
    }

    private static double valorAoLadoDe(XSSFSheet aba, String rotulo) {
        for (Row row : aba) {
            Cell cell = row.getCell(0);
            if (cell != null && cell.getCellType() == CellType.STRING && rotulo.equals(cell.getStringCellValue())) {
                return row.getCell(1).getNumericCellValue();
            }
        }
        throw new AssertionError("Sem a linha " + rotulo);
    }

    private static List<String> textos(XSSFSheet aba) {
        List<String> textos = new ArrayList<>();
        aba.forEach(row -> row.forEach(cell -> {
            if (cell.getCellType() == CellType.STRING) {
                textos.add(cell.getStringCellValue());
            }
        }));
        return textos;
    }

    /** O SUBTOTAL do valor, acima da tabela. */
    private static double totalDaAba(XSSFSheet aba) {
        Row totais = aba.getRow(aba.getTables().getFirst().getStartCellReference().getRow() - 2);
        return totais.getCell(totais.getLastCellNum() - 1).getNumericCellValue();
    }

    @Test
    void asRotasRespondemComOsDadosDaEscolaDoToken() throws Exception {
        UUID escola = escolaComMovimento();
        String token = "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), "FINANCEIRO", escola);

        mockMvc.perform(get("/relatorios/financeiro?de=2025-03-05&ate=2025-05-31")
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recebido.valor").value(335.00))
                .andExpect(jsonPath("$.inadimplencia.percentual").value(48.6))
                .andExpect(jsonPath("$.receitasEDespesas[0].mes").value("2025-03"))
                .andExpect(jsonPath("$.formasPagamento[2].forma").isEmpty())
                .andExpect(jsonPath("$.resumo.porCurso[0].grupo").value("Ensino Medio"))
                .andExpect(jsonPath("$.resumo.total.grupo").isEmpty());

        byte[] arquivo = mockMvc.perform(get("/relatorios/financeiro/exportar?de=2025-03-05&ate=2025-05-31"
                + "&conjuntos=PAGAMENTOS").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(arquivo, StandardCharsets.UTF_8)).contains("Pagamentos", "Ana Souza;Mensalidade;195,00;PIX");

        mockMvc.perform(get("/relatorios/financeiro/planilha?de=2025-03-05&ate=2025-05-31")
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".xlsx")));
    }

    @Test
    void cobrancaCriadaEntraNoTipoEscolhidoENoCursoDaUnicaMatriculaAtiva() {
        UUID escola = dados.escola();
        UUID comUmaMatricula = dados.aluno(escola, "Ana Souza");
        dados.matriculaNoCurso(escola, comUmaMatricula, "Ensino Medio");
        UUID comDuasMatriculas = dados.aluno(escola, "Bruno Lima");
        dados.matriculaNoCurso(escola, comDuasMatriculas, "Ensino Medio");
        dados.matriculaNoCurso(escola, comDuasMatriculas, "Curso de Ingles");
        UUID semMatricula = dados.aluno(escola, "Carla Dias");
        UUID financeiro = dados.usuario(escola, "FINANCEIRO", "Fernanda Rocha");
        LocalDate vencimento = LocalDate.now(FUSO_ESCOLA).plusDays(10);

        autenticarComo(financeiro, "FINANCEIRO", escola);
        cobrancaService.criar(new CobrancaRequest(comUmaMatricula, null, null, "Livro", reais("100.00"), vencimento,
                TipoCobranca.MATERIAL));
        cobrancaService.criar(new CobrancaRequest(comDuasMatriculas, null, null, "Livro", reais("20.00"), vencimento,
                TipoCobranca.MATERIAL));
        cobrancaService.criar(new CobrancaRequest(semMatricula, null, null, "Taxa", reais("10.00"), vencimento, null));

        RelatorioFinanceiroResponse.Resumo resumo = relatorioFinanceiroService.gerar(vencimento, vencimento).resumo();

        assertThat(resumo.porCurso()).extracting(Linha::grupo).containsExactly("Ensino Medio", "Sem curso");
        assertThat(resumo.porCurso().get(0).cobrado()).isEqualByComparingTo("100.00");
        assertThat(resumo.porCurso().get(1).cobrado()).isEqualByComparingTo("30.00");
        assertThat(resumo.porTipo()).extracting(Linha::grupo).containsExactly("MATERIAL", "OUTRO");
        assertThat(resumo.porTipo().get(0).cobrado()).isEqualByComparingTo("120.00");
    }
}
