package br.pucminas.eduka.backend.service.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.FormaPagamentoManual;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.dto.financeiro.AtualizarCobrancaRequest;
import br.pucminas.eduka.backend.dto.financeiro.BaixaManualRequest;
import br.pucminas.eduka.backend.dto.financeiro.CobrancaResponse;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaJaPagaException;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaNaoEditavelException;
import br.pucminas.eduka.backend.exception.financeiro.PagamentoEmProcessamentoException;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Editar e cancelar cobranca ligados ao provedor (R24, spec §8.5). O roteador
 * e mock; o gerenciador de transacao marca a transacao como ativa, para o
 * teste conferir que o provedor e chamado fora dela.
 */
class CobrancaServiceProvedorTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID COBRANCA = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-10-20T15:00:00Z");
    /** No dia de AGORA no fuso da escola (12h em Sao Paulo). */
    private static final BaixaManualRequest BAIXA_DE_HOJE = new BaixaManualRequest(new BigDecimal("500.00"),
            LocalDate.of(2026, 10, 20), FormaPagamentoManual.PIX);
    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 10, 25);

    private CobrancaRepository cobrancaRepository;
    private AuditoriaService auditoriaService;
    private PaymentRoutingService paymentRoutingService;
    private AvisoCobrancaService avisoCobrancaService;
    private Aluno aluno;
    private CobrancaService service;

    @BeforeEach
    void setUp() {
        cobrancaRepository = mock(CobrancaRepository.class);
        auditoriaService = mock(AuditoriaService.class);
        paymentRoutingService = mock(PaymentRoutingService.class);
        avisoCobrancaService = mock(AvisoCobrancaService.class);
        aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(UUID.randomUUID())
                .matricula("2026-001").build();
        when(cobrancaRepository.saveAndFlush(any(Cobranca.class))).thenAnswer(i -> i.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(UUID.randomUUID(), "FINANCEIRO", ESCOLA), null, "ROLE_FINANCEIRO"));

        service = new CobrancaService(cobrancaRepository, mock(AlunoRepository.class),
                mock(MatriculaTurmaRepository.class), mock(AlunoResponsavelRepository.class),
                mock(PessoaRepository.class), new IsolamentoEscola(), auditoriaService,
                avisoCobrancaService, mock(PagamentoCobrancaRepository.class), paymentRoutingService,
                new TransacaoQueMarcaAtiva(), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ---- R24: editar ----

    @ParameterizedTest
    @EnumSource(value = SituacaoProvedor.class, names = { "EMITINDO", "EMITIDA" })
    void depoisDoPagarNaoEdita(SituacaoProvedor situacao) {
        Cobranca cobranca = cobranca(StatusCobranca.PENDENTE, situacao);
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.of(cobranca));
        when(paymentRoutingService.permiteEditar(cobranca)).thenReturn(false);

        assertThatThrownBy(() -> service.atualizar(COBRANCA, edicao()))
                .isInstanceOf(CobrancaNaoEditavelException.class);
        assertThat(cobranca.getValor()).isEqualTo(new BigDecimal("500.00"));
        verify(cobrancaRepository, never()).saveAndFlush(any());
    }

    @Test
    void antesDoPagarEdita() {
        Cobranca cobranca = cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.NAO_EMITIDA);
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.of(cobranca));
        when(paymentRoutingService.permiteEditar(cobranca)).thenReturn(true);

        CobrancaResponse resposta = service.atualizar(COBRANCA, edicao());

        assertThat(resposta.valor()).isEqualTo(new BigDecimal("650.00"));
        verify(auditoriaService).registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.COBRANCA, COBRANCA);
    }

    // ---- Spec §8.5: cancelar ----

    @Test
    void cancelarChamaOProvedorForaDaTransacaoEDepoisCancela() {
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA)))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA)));
        boolean[] transacaoNaChamada = new boolean[1];
        doAnswer(i -> {
            transacaoNaChamada[0] = TransactionSynchronizationManager.isActualTransactionActive();
            return null;
        }).when(paymentRoutingService).cancelarNoProvedor(any());

        CobrancaResponse resposta = service.cancelar(COBRANCA);

        assertThat(transacaoNaChamada[0]).isFalse();
        assertThat(resposta.status()).isEqualTo(StatusCobranca.CANCELADA);
        assertThat(resposta.canceladaEm()).isEqualTo(AGORA);
        InOrder ordem = inOrder(paymentRoutingService, cobrancaRepository, auditoriaService);
        ordem.verify(paymentRoutingService).cancelarNoProvedor(any());
        ordem.verify(cobrancaRepository).saveAndFlush(any());
        ordem.verify(auditoriaService).registrar(OperacaoAuditoria.CANCELAR, EntidadeAuditada.COBRANCA, COBRANCA);
    }

    @Test
    void cancelarJaPagaRecusaSemChamarOProvedor() {
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PAGA, SituacaoProvedor.EMITIDA)));

        assertThatThrownBy(() -> service.cancelar(COBRANCA)).isInstanceOf(CobrancaJaPagaException.class);
        verifyNoInteractions(paymentRoutingService, auditoriaService);
    }

    @Test
    void cancelarPagaNoProvedorNaoCancela() {
        Cobranca cobranca = cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA);
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.of(cobranca));
        doThrow(new CobrancaJaPagaException()).when(paymentRoutingService).cancelarNoProvedor(any());

        assertThatThrownBy(() -> service.cancelar(COBRANCA)).isInstanceOf(CobrancaJaPagaException.class);
        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.PENDENTE);
        verify(cobrancaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void pagarNoMeioDoCancelamentoRecusa() {
        Cobranca depois = cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITINDO);
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.NAO_EMITIDA)))
                .thenReturn(Optional.of(depois));

        assertThatThrownBy(() -> service.cancelar(COBRANCA)).isInstanceOf(PagamentoEmProcessamentoException.class);
        assertThat(depois.getStatus()).isEqualTo(StatusCobranca.PENDENTE);
        verify(cobrancaRepository, never()).saveAndFlush(any());
    }

    @Test
    void avisoDaRemocaoQueChegouAntesSoFaltaAuditar() {
        Cobranca jaCancelada = cobranca(StatusCobranca.CANCELADA, SituacaoProvedor.EMITIDA);
        jaCancelada.setCanceladaEm(AGORA);
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA)))
                .thenReturn(Optional.of(jaCancelada));

        CobrancaResponse resposta = service.cancelar(COBRANCA);

        assertThat(resposta.status()).isEqualTo(StatusCobranca.CANCELADA);
        verify(cobrancaRepository, never()).saveAndFlush(any());
        verify(auditoriaService).registrar(OperacaoAuditoria.CANCELAR, EntidadeAuditada.COBRANCA, COBRANCA);
    }

    // ---- Baixa manual: tira a cobranca do provedor antes (sem pagamento em dobro) ----

    @Test
    void baixaManualRemoveNoProvedorForaDaTransacaoEDepoisMarcaPaga() {
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA)))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA)));
        boolean[] transacaoNaChamada = new boolean[1];
        doAnswer(i -> {
            transacaoNaChamada[0] = TransactionSynchronizationManager.isActualTransactionActive();
            return null;
        }).when(paymentRoutingService).cancelarNoProvedor(any());

        CobrancaResponse resposta = service.confirmarPagamento(COBRANCA, BAIXA_DE_HOJE);

        assertThat(transacaoNaChamada[0]).isFalse();
        assertThat(resposta.status()).isEqualTo(StatusCobranca.PAGA);
        assertThat(resposta.pagaEm()).isEqualTo(AGORA);
        InOrder ordem = inOrder(paymentRoutingService, cobrancaRepository, auditoriaService, avisoCobrancaService);
        ordem.verify(paymentRoutingService).cancelarNoProvedor(any());
        ordem.verify(cobrancaRepository).saveAndFlush(any());
        ordem.verify(auditoriaService).registrar(OperacaoAuditoria.PAGAR, EntidadeAuditada.COBRANCA, COBRANCA);
        ordem.verify(avisoCobrancaService).publicar(eq(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO),
                any(CobrancaParaAviso.class));
    }

    @Test
    void baixaManualDeCobrancaJaPagaNoProvedorRecusaSemBaixa() {
        Cobranca cobranca = cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA);
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.of(cobranca));
        doThrow(new CobrancaJaPagaException()).when(paymentRoutingService).cancelarNoProvedor(any());

        assertThatThrownBy(() -> service.confirmarPagamento(COBRANCA, BAIXA_DE_HOJE))
                .isInstanceOf(CobrancaJaPagaException.class)
                .hasMessageContaining("pagamento online");
        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.PENDENTE);
        verify(cobrancaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaService, avisoCobrancaService);
    }

    @Test
    void pagarNoMeioDaBaixaManualRecusa() {
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.NAO_EMITIDA)))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITINDO)));

        assertThatThrownBy(() -> service.confirmarPagamento(COBRANCA, BAIXA_DE_HOJE))
                .isInstanceOf(PagamentoEmProcessamentoException.class);
        verify(cobrancaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(avisoCobrancaService);
    }

    @Test
    void canceladaDuranteABaixaManualRecusaSemVirarPaga() {
        Cobranca jaCancelada = cobranca(StatusCobranca.CANCELADA, SituacaoProvedor.EMITIDA);
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA)))
                .thenReturn(Optional.of(jaCancelada));

        assertThatThrownBy(() -> service.confirmarPagamento(COBRANCA, BAIXA_DE_HOJE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jaCancelada.getStatus()).isEqualTo(StatusCobranca.CANCELADA);
        verifyNoInteractions(avisoCobrancaService);
    }

    @Test
    void baixaManualDeCobrancaJaPagaNoEdukaNemChamaOProvedor() {
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.PAGA, SituacaoProvedor.EMITIDA)));

        assertThatThrownBy(() -> service.confirmarPagamento(COBRANCA, BAIXA_DE_HOJE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(paymentRoutingService, avisoCobrancaService);
    }

    @Test
    void baixaManualComDiaPassadoGuardaAMeiaNoiteDoDiaNaEscolaEOValorRecebido() {
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA))
                .thenReturn(Optional.of(cobranca(StatusCobranca.EM_ATRASO, SituacaoProvedor.NAO_EMITIDA)));

        CobrancaResponse resposta = service.confirmarPagamento(COBRANCA, new BaixaManualRequest(
                new BigDecimal("515.30"), LocalDate.of(2026, 10, 3), FormaPagamentoManual.TRANSFERENCIA));

        // 03/10 00:00 em Sao Paulo (UTC-3) e 03:00 em UTC.
        assertThat(resposta.pagaEm()).isEqualTo(Instant.parse("2026-10-03T03:00:00Z"));
        assertThat(resposta.pagamento().dataPagamento()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(resposta.pagamento().valorPago()).isEqualByComparingTo("515.30");
        assertThat(resposta.pagamento().formaManual()).isEqualTo(FormaPagamentoManual.TRANSFERENCIA);
    }

    @Test
    void baixaManualComDiaFuturoNaEscolaRecusaSemLerACobranca() {
        // AGORA e 20/10 em Sao Paulo: 21/10 ainda nao chegou para a escola.
        BaixaManualRequest amanha = new BaixaManualRequest(new BigDecimal("500.00"), LocalDate.of(2026, 10, 21),
                FormaPagamentoManual.DINHEIRO);

        assertThatThrownBy(() -> service.confirmarPagamento(COBRANCA, amanha))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataPagamento");
        verifyNoInteractions(cobrancaRepository, paymentRoutingService, auditoriaService, avisoCobrancaService);
    }

    // ---- apoio ----

    private Cobranca cobranca(StatusCobranca status, SituacaoProvedor situacao) {
        return Cobranca.builder().id(COBRANCA).escolaId(ESCOLA).aluno(aluno).descricao("Mensalidade outubro")
                .valor(new BigDecimal("500.00")).dataVencimento(VENCIMENTO).status(status).situacaoProvedor(situacao)
                .build();
    }

    private static AtualizarCobrancaRequest edicao() {
        return new AtualizarCobrancaRequest(null, null, "Mensalidade outubro", new BigDecimal("650.00"), VENCIMENTO);
    }

    /** Como o JpaTransactionManager: a transacao fica ativa do inicio ao commit ou rollback. */
    private static final class TransacaoQueMarcaAtiva implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        @Override
        public void rollback(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
