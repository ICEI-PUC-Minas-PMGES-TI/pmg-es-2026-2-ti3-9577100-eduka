package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.FormaPagamentoManual;
import br.pucminas.eduka.backend.domain.financeiro.OrigemPagamento;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.financeiro.AtualizarCobrancaRequest;
import br.pucminas.eduka.backend.dto.financeiro.BaixaManualRequest;
import br.pucminas.eduka.backend.dto.financeiro.CobrancaResponse;
import br.pucminas.eduka.backend.dto.financeiro.PagamentoDaCobrancaResponse;
import br.pucminas.eduka.backend.dto.financeiro.PaginaCobrancasResponse.TotalPorStatus;
import br.pucminas.eduka.backend.dto.financeiro.PaginaCobrancasResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaJaPagaException;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaFiltro;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.service.financeiro.CobrancaService;
import br.pucminas.eduka.backend.service.financeiro.DashboardFinanceiroService;

/**
 * Mudancas de situacao da cobranca feitas pela escola (baixa manual, cancelar,
 * editar) gravadas no banco e auditadas, e o que a escola nao pode fazer:
 * mexer de novo no que ja foi pago ou cancelado, ou em cobranca de outra
 * escola.
 */
class CobrancaSituacaoBancoRealTest extends BancoRealTest {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private CobrancaService cobrancaService;

    @Autowired
    private CobrancaRepository cobrancaRepository;

    @Autowired
    private DashboardFinanceiroService dashboardFinanceiroService;

    private UUID escola;
    private UUID aluno;
    private UUID cobranca;

    @BeforeEach
    void cobrancaPendente() {
        escola = dados.escola();
        aluno = dados.aluno(escola);
        cobranca = dados.cobranca(escola, aluno, StatusCobranca.PENDENTE, LocalDate.now().plusDays(10));
        autenticarComo(dados.usuario(escola, "FINANCEIRO", "Fernanda Rocha"), "FINANCEIRO", escola);
    }

    private static AtualizarCobrancaRequest edicao(LocalDate vencimento) {
        return new AtualizarCobrancaRequest(null, null, "Mensalidade", new BigDecimal("120.00"), vencimento);
    }

    /** R$ 95,00 em dinheiro hoje, abaixo dos R$ 100,00 da cobranca (desconto). */
    private static BaixaManualRequest baixaDeHoje() {
        return new BaixaManualRequest(new BigDecimal("95.00"), LocalDate.now(FUSO_ESCOLA),
                FormaPagamentoManual.DINHEIRO);
    }

    @Test
    void baixaManualPagaUmaVezSo() {
        CobrancaResponse paga = cobrancaService.confirmarPagamento(cobranca, baixaDeHoje());

        assertThat(paga.status()).isEqualTo(StatusCobranca.PAGA);
        assertThat(paga.pagaEm()).isNotNull();
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PAGA);
        // Relido do banco: valor, dia e forma da baixa ficam na cobranca, sem PAGAMENTO_COBRANCA.
        PagamentoDaCobrancaResponse pagamento = cobrancaService.buscar(cobranca).pagamento();
        assertThat(pagamento.origem()).isEqualTo(OrigemPagamento.MANUAL);
        assertThat(pagamento.valorPago()).isEqualByComparingTo("95.00");
        assertThat(pagamento.dataPagamento()).isEqualTo(LocalDate.now(FUSO_ESCOLA));
        assertThat(pagamento.formaManual()).isEqualTo(FormaPagamentoManual.DINHEIRO);
        assertThat(dados.valor(cobranca)).isEqualByComparingTo("100.00");

        // Paga nao volta atras pela escola: nem paga de novo, nem cancela, nem edita.
        assertThatThrownBy(() -> cobrancaService.confirmarPagamento(cobranca, baixaDeHoje()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cobrancaService.cancelar(cobranca)).isInstanceOf(CobrancaJaPagaException.class);
        assertThatThrownBy(() -> cobrancaService.atualizar(cobranca, edicao(LocalDate.now().plusDays(5))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PAGA);
        assertThat(dados.operacoesNoLog(cobranca)).containsExactly(OperacaoAuditoria.PAGAR);
    }

    @Test
    void baixaComDiaPassadoEntraNoRecebidoDaqueleDiaComOValorInformado() {
        LocalDate diaDoPagamento = LocalDate.now(FUSO_ESCOLA).minusDays(3);

        cobrancaService.confirmarPagamento(cobranca, new BaixaManualRequest(new BigDecimal("87.40"), diaDoPagamento,
                FormaPagamentoManual.TRANSFERENCIA));

        Instant inicioDoDia = diaDoPagamento.atStartOfDay(FUSO_ESCOLA).toInstant();
        // O painel e os relatorios somam pelo PAGA_EM: a baixa conta no dia do pagamento, nao no da baixa.
        assertThat(cobrancaRepository.recebidosEntre(escola, inicioDoDia, inicioDoDia.plus(Duration.ofDays(1))))
                .singleElement()
                .satisfies(recebido -> assertThat(recebido.getValor()).isEqualByComparingTo("87.40"));
        assertThat(cobrancaService.buscar(cobranca).pagamento().dataPagamento()).isEqualTo(diaDoPagamento);
        assertThat(dashboardFinanceiroService.painel().ultimosPagamentos()).singleElement().satisfies(recente -> {
            assertThat(recente.valor()).isEqualByComparingTo("87.40");
            assertThat(recente.formaManual()).isEqualTo(FormaPagamentoManual.TRANSFERENCIA);
            assertThat(recente.pagoEm()).isEqualTo(inicioDoDia);
        });
    }

    @Test
    void canceladaEFinal() {
        CobrancaResponse cancelada = cobrancaService.cancelar(cobranca);

        assertThat(cancelada.status()).isEqualTo(StatusCobranca.CANCELADA);
        assertThat(cancelada.canceladaEm()).isNotNull();

        assertThatThrownBy(() -> cobrancaService.cancelar(cobranca)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cobrancaService.confirmarPagamento(cobranca, baixaDeHoje()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cobrancaService.atualizar(cobranca, edicao(LocalDate.now().plusDays(5))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.CANCELADA);
        assertThat(dados.operacoesNoLog(cobranca)).containsExactly(OperacaoAuditoria.CANCELAR);
    }

    @Test
    void editarOVencimentoAcertaOAtraso() {
        CobrancaResponse vencida = cobrancaService.atualizar(cobranca, edicao(LocalDate.now().minusDays(3)));

        assertThat(vencida.status()).isEqualTo(StatusCobranca.EM_ATRASO);
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.EM_ATRASO);

        CobrancaResponse prorrogada = cobrancaService.atualizar(cobranca, edicao(LocalDate.now().plusDays(7)));

        assertThat(prorrogada.status()).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(dados.valor(cobranca)).isEqualByComparingTo("120.00");
        assertThat(dados.operacoesNoLog(cobranca)).containsExactly(OperacaoAuditoria.EDITAR, OperacaoAuditoria.EDITAR);
    }

    @Test
    void emAtrasoAindaPodeSerPaga() {
        UUID atrasada = dados.cobranca(escola, aluno, StatusCobranca.EM_ATRASO, LocalDate.now().minusDays(15));

        assertThat(cobrancaService.confirmarPagamento(atrasada, baixaDeHoje()).status()).isEqualTo(StatusCobranca.PAGA);
    }

    @Test
    void listagemPaginadaSomaOsTotaisDeCadaSituacao() {
        dados.cobranca(escola, aluno, StatusCobranca.PENDENTE, LocalDate.now().minusDays(2), new BigDecimal("50.00"));
        dados.cobranca(escola, aluno, StatusCobranca.PAGA, LocalDate.now(), new BigDecimal("30.00"));
        dados.cobranca(escola, aluno, StatusCobranca.CANCELADA, LocalDate.now(), new BigDecimal("20.00"));
        UUID outraEscola = dados.escola();
        dados.cobranca(outraEscola, dados.aluno(outraEscola), StatusCobranca.PAGA, LocalDate.now(),
                new BigDecimal("999.00"));

        PaginaCobrancasResponse pagina = cobrancaService
                .listarPaginado(new CobrancaFiltro(null, null, null, null, null), null, 0, 20);

        assertThat(pagina.totalElementos()).isEqualTo(4);
        // A pendente vencida conta como EM_ATRASO, mesmo antes da rotina diaria marcar.
        assertThat(pagina.totaisPorStatus())
                .extracting(TotalPorStatus::status, TotalPorStatus::quantidade, t -> t.valor().intValue())
                .containsExactly(tuple(StatusCobranca.PENDENTE, 1L, 100), tuple(StatusCobranca.EM_ATRASO, 1L, 50),
                        tuple(StatusCobranca.PAGA, 1L, 30), tuple(StatusCobranca.CANCELADA, 1L, 20));
    }

    @Test
    void buscaPeloNomeDoAlunoRestringeAPaginaMasNaoOsTotais() {
        UUID mariana = dados.aluno(escola, "Mariana Souza");
        UUID daMariana = dados.cobranca(escola, mariana, StatusCobranca.PAGA, LocalDate.now());
        UUID outraEscola = dados.escola();
        dados.cobranca(outraEscola, dados.aluno(outraEscola, "Mariana Lima"), StatusCobranca.PAGA, LocalDate.now());
        CobrancaFiltro semFiltro = new CobrancaFiltro(null, null, null, null, null);

        PaginaCobrancasResponse pagina = cobrancaService.listarPaginado(semFiltro, "  MARIANA ", 0, 20);

        assertThat(pagina.conteudo()).extracting(CobrancaResponse::id).containsExactly(daMariana);
        assertThat(pagina.totalElementos()).isEqualTo(1);
        // As abas continuam mostrando a escola inteira: a do setUp (PENDENTE) e a da Mariana (PAGA).
        assertThat(pagina.totaisPorStatus()).extracting(TotalPorStatus::quantidade)
                .containsExactly(1L, 0L, 1L, 0L);

        PaginaCobrancasResponse ninguem = cobrancaService.listarPaginado(semFiltro, "Zuleica", 0, 20);

        assertThat(ninguem.conteudo()).isEmpty();
        assertThat(ninguem.totalElementos()).isZero();
    }

    @Test
    void financeiroDeOutraEscolaNaoEnxergaNemMexeNaCobranca() {
        UUID outraEscola = dados.escola();
        autenticarComo(dados.usuario(outraEscola, "FINANCEIRO", "Outro Financeiro"), "FINANCEIRO", outraEscola);

        assertThatThrownBy(() -> cobrancaService.buscar(cobranca)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> cobrancaService.confirmarPagamento(cobranca, baixaDeHoje()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> cobrancaService.cancelar(cobranca)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> cobrancaService.atualizar(cobranca, edicao(LocalDate.now().plusDays(5))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(cobrancaService.listar(null, null, null, null)).extracting(CobrancaResponse::id)
                .doesNotContain(cobranca);

        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(dados.valor(cobranca)).isEqualByComparingTo("100.00");
        assertThat(dados.operacoesNoLog(cobranca)).isEmpty();
    }
}
