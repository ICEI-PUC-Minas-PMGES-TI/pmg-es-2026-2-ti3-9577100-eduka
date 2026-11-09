package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.CobrancaDaSemana;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.Mes;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.PagamentoRecente;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.service.DashboardFinanceiroService;

/**
 * As contas do painel financeiro sobre dados gravados no Postgres: valores,
 * datas no fuso da escola, nomes decifrados e isolamento entre escolas.
 */
class DashboardFinanceiroBancoRealTest extends BancoRealTest {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private DashboardFinanceiroService dashboardFinanceiroService;

    private final LocalDate hoje = LocalDate.now(FUSO_ESCOLA);
    private final YearMonth mesAtual = YearMonth.from(hoje);
    /** Instantes deste mes que nao dependem da hora em que o teste roda. */
    private final Instant inicioDoMes = mesAtual.atDay(1).atStartOfDay(FUSO_ESCOLA).toInstant();
    private final Instant meioDoMesPassado = mesAtual.minusMonths(1).atDay(15).atStartOfDay(FUSO_ESCOLA)
            .toInstant();

    private static BigDecimal reais(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void painelSomaSoOQueEDaEscola() {
        UUID escola = dados.escola();
        UUID ana = dados.aluno(escola, "Ana Souza");
        UUID bruno = dados.aluno(escola, "Bruno Lima");
        UUID carla = dados.aluno(escola, "Carla Dias");
        LocalDate venceuSemanaPassada = hoje.minusDays(5);

        // Recebidos: online (vale o valor pago no provedor), baixa manual e um do mes passado.
        UUID pagaOnline = dados.cobrancaPaga(escola, ana, reais("200.00"), venceuSemanaPassada,
                inicioDoMes.plus(Duration.ofHours(2)));
        dados.pagamentoOnline(escola, pagaOnline, reais("195.00"), FormaPagamento.PIX);
        UUID pagaManual = dados.cobrancaPaga(escola, bruno, reais("100.00"), venceuSemanaPassada,
                inicioDoMes.plus(Duration.ofHours(1)));
        dados.cobrancaPaga(escola, carla, reais("50.00"), hoje.minusDays(40), meioDoMesPassado);

        // Em aberto: uma em atraso, uma que vence na semana e uma que vence depois.
        UUID atrasada = dados.cobranca(escola, ana, StatusCobranca.PENDENTE, hoje.minusDays(10), reais("300.00"));
        UUID naSemana = dados.cobranca(escola, bruno, StatusCobranca.PENDENTE, hoje.plusDays(3), reais("150.00"));
        dados.cobranca(escola, carla, StatusCobranca.PENDENTE, hoje.plusDays(20), reais("400.00"));
        dados.cobranca(escola, carla, StatusCobranca.CANCELADA, hoje.minusDays(3), reais("999.00"));

        UUID categoria = dados.categoriaDespesa(escola);
        dados.despesa(escola, categoria, reais("90.00"), mesAtual.atEndOfMonth(), SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, categoria, reais("60.00"), hoje.minusDays(3), SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, categoria, reais("500.00"), mesAtual.plusMonths(1).atDay(10), SituacaoDespesa.A_PAGAR,
                null);
        dados.despesa(escola, categoria, reais("40.00"), hoje, SituacaoDespesa.PAGA,
                inicioDoMes.plus(Duration.ofHours(3)));

        UUID fernanda = dados.usuario(escola, "FINANCEIRO", "Fernanda Rocha");
        dados.acaoNoLog(escola, fernanda, OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, naSemana,
                Instant.now().minusSeconds(60));
        dados.acaoNoLog(escola, fernanda, OperacaoAuditoria.PAGAR, EntidadeAuditada.COBRANCA, pagaManual,
                Instant.now());

        // Outra escola, com de tudo um pouco: nada disso pode aparecer.
        UUID outra = dados.escola();
        UUID alunoDeOutra = dados.aluno(outra);
        dados.cobrancaPaga(outra, alunoDeOutra, reais("7000.00"), venceuSemanaPassada, inicioDoMes);
        dados.cobranca(outra, alunoDeOutra, StatusCobranca.PENDENTE, hoje.minusDays(1), reais("8000.00"));
        dados.despesa(outra, dados.categoriaDespesa(outra), reais("9000.00"), hoje, SituacaoDespesa.A_PAGAR, null);
        dados.acaoNoLog(outra, dados.usuario(outra, "MASTER", "Outro"), OperacaoAuditoria.CRIAR,
                EntidadeAuditada.COBRANCA, UUID.randomUUID(), Instant.now().plusSeconds(60));

        autenticarComo("FINANCEIRO", escola);
        DashboardFinanceiroResponse painel = dashboardFinanceiroService.painel();

        assertThat(painel.recebidoNoMes().valor()).isEqualByComparingTo("295.00");
        assertThat(painel.recebidoNoMes().quantidade()).isEqualTo(2);
        assertThat(painel.faltaReceber().valor()).isEqualByComparingTo("850.00");
        assertThat(painel.faltaReceber().quantidade()).isEqualTo(3);

        assertThat(painel.emAtraso().alunos()).isEqualTo(1);
        assertThat(painel.emAtraso().cobrancas()).isEqualTo(1);
        assertThat(painel.emAtraso().valor()).isEqualByComparingTo("300.00");
        // 300 em atraso sobre 650 que ja venceu (300 + as pagas de 200, 100 e 50).
        assertThat(painel.emAtraso().inadimplencia()).isEqualTo(46);

        assertThat(painel.despesasAPagar().valor()).isEqualByComparingTo("150.00");
        assertThat(painel.despesasAPagar().quantidade()).isEqualTo(2);
        assertThat(painel.despesasAPagar().proximoVencimento()).isEqualTo(mesAtual.atEndOfMonth());

        // Os 12 ultimos meses, do mais antigo ao atual: a tela escolhe quantos mostra.
        assertThat(painel.ultimosMeses()).extracting(Mes::mes)
                .containsExactlyElementsOf(Stream.iterate(mesAtual.minusMonths(11), m -> m.plusMonths(1)).limit(12)
                        .toList());
        Mes atual = painel.ultimosMeses().getLast();
        assertThat(atual.entrou()).isEqualByComparingTo("295.00");
        assertThat(atual.saiu()).isEqualByComparingTo("40.00");
        assertThat(painel.ultimosMeses().get(10).entrou()).isEqualByComparingTo("50.00");

        assertThat(painel.vencemNaSemana()).extracting(CobrancaDaSemana::id).containsExactly(atrasada, naSemana);
        CobrancaDaSemana primeira = painel.vencemNaSemana().getFirst();
        assertThat(primeira.alunoNome()).isEqualTo("Ana Souza");
        assertThat(primeira.status()).isEqualTo(StatusCobranca.EM_ATRASO);
        assertThat(painel.vencemNaSemana().get(1).status()).isEqualTo(StatusCobranca.PENDENTE);

        List<PagamentoRecente> pagamentos = painel.ultimosPagamentos();
        assertThat(pagamentos).hasSize(3);
        PagamentoRecente online = pagamentos.getFirst();
        assertThat(online.cobrancaId()).isEqualTo(pagaOnline);
        assertThat(online.alunoNome()).isEqualTo("Ana Souza");
        assertThat(online.valor()).isEqualByComparingTo("195.00");
        assertThat(online.forma()).isEqualTo(FormaPagamento.PIX);
        assertThat(online.origem()).isEqualTo(OrigemPagamento.ONLINE);
        PagamentoRecente manual = pagamentos.get(1);
        assertThat(manual.cobrancaId()).isEqualTo(pagaManual);
        assertThat(manual.forma()).isNull();
        assertThat(manual.origem()).isEqualTo(OrigemPagamento.MANUAL);

        assertThat(painel.ultimasAcoes()).hasSize(2);
        assertThat(painel.ultimasAcoes().getFirst().operacao()).isEqualTo(OperacaoAuditoria.PAGAR);
        assertThat(painel.ultimasAcoes().getFirst().usuarioNome()).isEqualTo("Fernanda Rocha");
        assertThat(painel.ultimasAcoes().getFirst().perfil()).isEqualTo(PerfilUsuario.FINANCEIRO);
    }

    @Test
    void pagamentoAntigoFicaForaDoGraficoMasNaoDasOutrasContas() {
        UUID escola = dados.escola();
        UUID aluno = dados.aluno(escola, "Davi Alves");
        Instant haUmAno = mesAtual.minusMonths(12).atDay(10).atStartOfDay(FUSO_ESCOLA).toInstant();
        UUID antiga = dados.cobrancaPaga(escola, aluno, reais("80.00"), hoje.minusYears(1), haUmAno);

        autenticarComo("FINANCEIRO", escola);
        DashboardFinanceiroResponse painel = dashboardFinanceiroService.painel();

        // O grafico so le os seus 12 meses...
        assertThat(painel.ultimosMeses()).allSatisfy(m -> assertThat(m.entrou()).isZero());
        assertThat(painel.recebidoNoMes().quantidade()).isZero();
        // ...mas os ultimos pagamentos e a inadimplencia olham o historico inteiro.
        assertThat(painel.ultimosPagamentos()).extracting(PagamentoRecente::cobrancaId).containsExactly(antiga);
        assertThat(painel.emAtraso().inadimplencia()).isZero();
    }

    /** Esse horario de {@code dia} no fuso da escola. */
    private static Instant em(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto).atZone(FUSO_ESCOLA).toInstant();
    }

    @Test
    void movimentoDoPeriodoContaSoOsDiasEscolhidosDosMesesDasPontas() {
        UUID escola = dados.escola();
        UUID aluno = dados.aluno(escola);
        UUID categoria = dados.categoriaDespesa(escola);
        LocalDate de = LocalDate.of(2025, 3, 5);
        LocalDate ate = LocalDate.of(2025, 5, 31);
        // Antes do primeiro dia e depois do ultimo (meia-noite e meia no fuso da escola): ficam de fora.
        dados.cobrancaPaga(escola, aluno, reais("1.00"), de, em(de.minusDays(1), 23, 59));
        dados.cobrancaPaga(escola, aluno, reais("2.00"), de, em(ate.plusDays(1), 0, 30));
        // Dentro, nas duas pontas e no meio.
        dados.cobrancaPaga(escola, aluno, reais("100.00"), de, em(de, 0, 0));
        UUID online = dados.cobrancaPaga(escola, aluno, reais("200.00"), de, em(LocalDate.of(2025, 4, 10), 12, 0));
        dados.pagamentoOnline(escola, online, reais("195.00"), FormaPagamento.PIX);
        dados.cobrancaPaga(escola, aluno, reais("50.00"), de, em(ate, 23, 59));
        dados.despesa(escola, categoria, reais("30.00"), de, SituacaoDespesa.PAGA, em(LocalDate.of(2025, 4, 2), 9, 0));
        dados.despesa(escola, categoria, reais("70.00"), de, SituacaoDespesa.PAGA, em(de.minusDays(3), 9, 0));
        UUID outra = dados.escola();
        dados.cobrancaPaga(outra, dados.aluno(outra), reais("999.00"), de, em(LocalDate.of(2025, 4, 10), 12, 0));

        autenticarComo("FINANCEIRO", escola);
        List<Mes> meses = dashboardFinanceiroService.movimento(de, ate);

        assertThat(meses).extracting(Mes::mes)
                .containsExactly(YearMonth.of(2025, 3), YearMonth.of(2025, 4), YearMonth.of(2025, 5));
        assertThat(meses.get(0).entrou()).isEqualByComparingTo("100.00");
        // No mes do meio vale o valor pago no provedor, como no painel.
        assertThat(meses.get(1).entrou()).isEqualByComparingTo("195.00");
        assertThat(meses.get(1).saiu()).isEqualByComparingTo("30.00");
        assertThat(meses.get(2).entrou()).isEqualByComparingTo("50.00");
        assertThat(meses.get(0).saiu()).isZero();
    }

    @Test
    void movimentoRecusaPeriodoInvertidoOuMaiorQue24Meses() {
        autenticarComo("FINANCEIRO", dados.escola());
        LocalDate janeiro = LocalDate.of(2025, 1, 1);

        assertThatThrownBy(() -> dashboardFinanceiroService.movimento(janeiro, janeiro.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dashboardFinanceiroService.movimento(janeiro, LocalDate.of(2027, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        // De janeiro de 2025 a dezembro de 2026 sao 24 meses: cabe.
        assertThat(dashboardFinanceiroService.movimento(janeiro, LocalDate.of(2026, 12, 31))).hasSize(24);
    }

    @Test
    void atrasosAntigosNaoEscondemOQueVenceNaSemana() {
        UUID escola = dados.escola();
        UUID ana = dados.aluno(escola, "Ana Souza");
        for (int dias = 30; dias < 42; dias++) {
            dados.cobranca(escola, ana, StatusCobranca.PENDENTE, hoje.minusDays(dias), reais("100.00"));
        }
        UUID naSemana = dados.cobranca(escola, ana, StatusCobranca.PENDENTE, hoje.plusDays(2), reais("150.00"));
        dados.cobranca(escola, ana, StatusCobranca.PENDENTE, hoje.plusDays(7), reais("150.00"));

        autenticarComo("FINANCEIRO", escola);
        List<CobrancaDaSemana> lista = dashboardFinanceiroService.painel().vencemNaSemana();

        // 10 das 12 em atraso, as mais antigas primeiro, e depois a da semana; a de daqui a 7 dias fica fora.
        assertThat(lista).hasSize(11);
        assertThat(lista.subList(0, 10)).allMatch(c -> c.status() == StatusCobranca.EM_ATRASO);
        assertThat(lista.getFirst().dataVencimento()).isEqualTo(hoje.minusDays(41));
        assertThat(lista.getLast().id()).isEqualTo(naSemana);
        assertThat(lista.getLast().status()).isEqualTo(StatusCobranca.PENDENTE);
    }

    @Test
    void escolaSemMovimentoTemPainelZerado() {
        autenticarComo("MASTER", dados.escola());

        DashboardFinanceiroResponse painel = dashboardFinanceiroService.painel();

        assertThat(painel.recebidoNoMes().valor()).isZero();
        assertThat(painel.faltaReceber().quantidade()).isZero();
        assertThat(painel.emAtraso().inadimplencia()).isNull();
        assertThat(painel.despesasAPagar().proximoVencimento()).isNull();
        assertThat(painel.ultimosMeses()).hasSize(12)
                .allSatisfy(m -> assertThat(m.entrou().add(m.saiu())).isZero());
        assertThat(painel.vencemNaSemana()).isEmpty();
        assertThat(painel.ultimosPagamentos()).isEmpty();
        assertThat(painel.ultimasAcoes()).isEmpty();
    }
}
