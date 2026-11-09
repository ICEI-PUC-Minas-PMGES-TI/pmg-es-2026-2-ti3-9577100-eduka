package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.exception.CobrancaNaoPagavelException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.SandboxIndisponivelException;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.PiiEncryptionProperties;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/** Atalhos de Sandbox (NOVA-6): aprovar a conta filha e confirmar o pagamento de uma cobranca emitida. */
class RoteadorSandboxTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID COBRANCA = UUID.randomUUID();
    private static final UUID USUARIO_ALUNO = UUID.randomUUID();
    private static final UUID FATURA = UUID.randomUUID();
    private static final String CHAVE_PAI = "$aact_hmlg_CHAVE_FICTICIA_DA_CONTA_PAI";
    private static final String CHAVE_SUBCONTA = "$aact_hmlg_CHAVE_FICTICIA_DA_SUBCONTA";

    private ContaRecebimentoEscolaRepository contaRepository;
    private CobrancaRepository cobrancaRepository;
    private AlunoRepository alunoRepository;
    private FaturaPlataformaRepository faturaRepository;
    private RoteadorEventos eventos;
    private PaymentProvider provider;
    private PiiCryptoService cripto;
    private RoteadorSandbox roteador;

    @BeforeEach
    void setUp() {
        contaRepository = mock(ContaRecebimentoEscolaRepository.class);
        cobrancaRepository = mock(CobrancaRepository.class);
        alunoRepository = mock(AlunoRepository.class);
        eventos = mock(RoteadorEventos.class);
        provider = mock(PaymentProvider.class);
        cripto = new PiiCryptoService(new PiiEncryptionProperties("u28qCTox5pjdroT6PVuzUItNOUfd1K0ySdEX/Rk7gXU=", 1));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(UUID.randomUUID(), "MASTER", ESCOLA), null, "ROLE_MASTER"));

        faturaRepository = mock(FaturaPlataformaRepository.class);
        roteador = new RoteadorSandbox(contaRepository, cobrancaRepository, alunoRepository, faturaRepository,
                eventos, new IsolamentoEscola(), cripto, new ChamadorProvedor(provider),
                new TransacaoQueMarcaAtiva());
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ---- Aprovar a conta ----

    @Test
    void aprovaAContaComAChaveDecifradaForaDaTransacao() {
        contaComChave();
        AtomicBoolean comTransacao = new AtomicBoolean(true);
        doAnswer(i -> {
            comTransacao.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(provider).sandboxAprovarConta(any());

        roteador.aprovarConta();

        verify(provider).sandboxAprovarConta(new CredencialProvedor(CHAVE_SUBCONTA));
        assertThat(comTransacao).isFalse();
    }

    @Test
    void semContaE404SemChamarOProvedor() {
        when(contaRepository.findByEscolaId(ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(roteador::aprovarConta).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void contaSemChaveNoProvedorE400() {
        when(contaRepository.findByEscolaId(ESCOLA))
                .thenReturn(Optional.of(ContaRecebimentoEscola.builder().escolaId(ESCOLA).build()));

        assertThatThrownBy(roteador::aprovarConta).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void foraDoSandboxAprovarESandboxIndisponivel() {
        contaComChave();
        doThrow(new IllegalStateException("Operacao disponivel so no Sandbox do Asaas"))
                .when(provider).sandboxAprovarConta(any());

        assertThatThrownBy(roteador::aprovarConta).isInstanceOf(SandboxIndisponivelException.class);
    }

    // ---- Confirmar o pagamento ----

    @Test
    void confirmaOPagamentoDaCobrancaEmitida() {
        contaComChave();
        cobranca(SituacaoProvedor.EMITIDA);

        roteador.confirmarPagamento(COBRANCA);

        verify(provider).sandboxConfirmarPagamento(new CredencialProvedor(CHAVE_SUBCONTA), "pay_123");
    }

    @Test
    void cobrancaDeOutraEscolaE404() {
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roteador.confirmarPagamento(COBRANCA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void cobrancaNaoEmitidaE400() {
        contaComChave();
        cobranca(SituacaoProvedor.NAO_EMITIDA);

        assertThatThrownBy(() -> roteador.confirmarPagamento(COBRANCA))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void foraDoSandboxConfirmarESandboxIndisponivel() {
        contaComChave();
        cobranca(SituacaoProvedor.EMITIDA);
        doThrow(new IllegalStateException("Operacao disponivel so no Sandbox do Asaas"))
                .when(provider).sandboxConfirmarPagamento(any(), any());

        assertThatThrownBy(() -> roteador.confirmarPagamento(COBRANCA))
                .isInstanceOf(SandboxIndisponivelException.class);
    }

    // ---- O aluno paga a propria cobranca ----

    @Test
    void alunoPagaEOPagamentoEGravadoSemEsperarOAviso() {
        Aluno aluno = alunoLogado();
        contaComChave();
        cobranca(SituacaoProvedor.EMITIDA, aluno);
        CobrancaProvedor recebida = noProvedor(StatusCobrancaProvedor.RECEBIDA);
        when(provider.consultarCobranca(new CredencialProvedor(CHAVE_SUBCONTA), "pay_123")).thenReturn(recebida);

        roteador.pagarComoAluno(COBRANCA);

        verify(provider).sandboxConfirmarPagamento(new CredencialProvedor(CHAVE_SUBCONTA), "pay_123");
        verify(eventos).registrarPagamentoEncontrado(COBRANCA, recebida);
    }

    @Test
    void aindaNaoPagaNoProvedorFicaParaOAviso() {
        Aluno aluno = alunoLogado();
        contaComChave();
        cobranca(SituacaoProvedor.EMITIDA, aluno);
        when(provider.consultarCobranca(any(), any()))
                .thenReturn(noProvedor(StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO));

        roteador.pagarComoAluno(COBRANCA);

        verify(eventos, never()).registrarPagamentoEncontrado(any(), any());
    }

    @Test
    void consultaQueFalhaNaoDesfazAConfirmacao() {
        Aluno aluno = alunoLogado();
        contaComChave();
        cobranca(SituacaoProvedor.EMITIDA, aluno);
        when(provider.consultarCobranca(any(), any())).thenThrow(new ProvedorIndisponivelException("timeout"));

        roteador.pagarComoAluno(COBRANCA);

        verify(provider).sandboxConfirmarPagamento(new CredencialProvedor(CHAVE_SUBCONTA), "pay_123");
        verify(eventos, never()).registrarPagamentoEncontrado(any(), any());
    }

    @Test
    void recusaDaCobrancaJaPagaNoProvedorGravaOPagamento() {
        Aluno aluno = alunoLogado();
        contaComChave();
        cobranca(SituacaoProvedor.EMITIDA, aluno);
        doThrow(new ProvedorRecusouException("Esta cobranca ja foi recebida")).when(provider)
                .sandboxConfirmarPagamento(any(), any());
        CobrancaProvedor recebida = noProvedor(StatusCobrancaProvedor.RECEBIDA);
        when(provider.consultarCobranca(new CredencialProvedor(CHAVE_SUBCONTA), "pay_123")).thenReturn(recebida);

        roteador.pagarComoAluno(COBRANCA);

        verify(eventos).registrarPagamentoEncontrado(COBRANCA, recebida);
    }

    @Test
    void recusaDaCobrancaAindaEmAbertoChegaAoAluno() {
        Aluno aluno = alunoLogado();
        contaComChave();
        cobranca(SituacaoProvedor.EMITIDA, aluno);
        doThrow(new ProvedorRecusouException("Motivo do Asaas")).when(provider)
                .sandboxConfirmarPagamento(any(), any());
        when(provider.consultarCobranca(any(), any()))
                .thenReturn(noProvedor(StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO));

        assertThatThrownBy(() -> roteador.pagarComoAluno(COBRANCA))
                .isInstanceOf(ProvedorRecusouException.class)
                .hasMessage("Motivo do Asaas");
        verify(eventos, never()).registrarPagamentoEncontrado(any(), any());
    }

    @Test
    void cobrancaJaPagaNoEdukaNaoChamaOProvedor() {
        Aluno aluno = alunoLogado();
        cobranca(SituacaoProvedor.EMITIDA, aluno);
        Cobranca cobranca = cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA).orElseThrow();
        when(cobranca.getStatus()).thenReturn(StatusCobranca.PAGA);

        roteador.pagarComoAluno(COBRANCA);

        verifyNoInteractions(provider, eventos);
    }

    @Test
    void cobrancaDeOutroAlunoE404() {
        alunoLogado();
        cobranca(SituacaoProvedor.EMITIDA, Aluno.builder().id(UUID.randomUUID()).build());

        assertThatThrownBy(() -> roteador.pagarComoAluno(COBRANCA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void cobrancaDoAlunoAindaNaoEmitidaE400() {
        Aluno aluno = alunoLogado();
        cobranca(SituacaoProvedor.NAO_EMITIDA, aluno);

        assertThatThrownBy(() -> roteador.pagarComoAluno(COBRANCA))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    // ---- O MASTER paga a fatura do Eduka ----

    @Test
    void masterPagaAFaturaNaContaPaiEOPagamentoEGravado() {
        fatura(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA);
        when(provider.credencialDaContaPai()).thenReturn(new CredencialProvedor(CHAVE_PAI));
        CobrancaProvedor recebida = noProvedor(StatusCobrancaProvedor.RECEBIDA);
        when(provider.consultarCobranca(new CredencialProvedor(CHAVE_PAI), "pay_fatura")).thenReturn(recebida);

        roteador.pagarFatura(FATURA);

        verify(provider).sandboxConfirmarPagamento(new CredencialProvedor(CHAVE_PAI), "pay_fatura");
        verify(eventos).registrarPagamentoDaFaturaEncontrado(FATURA, recebida);
    }

    @Test
    void recusaDaFaturaJaPagaNoProvedorGravaOPagamento() {
        fatura(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA);
        when(provider.credencialDaContaPai()).thenReturn(new CredencialProvedor(CHAVE_PAI));
        doThrow(new ProvedorRecusouException("Esta cobranca ja foi recebida")).when(provider)
                .sandboxConfirmarPagamento(any(), any());
        CobrancaProvedor recebida = noProvedor(StatusCobrancaProvedor.RECEBIDA);
        when(provider.consultarCobranca(any(), any())).thenReturn(recebida);

        roteador.pagarFatura(FATURA);

        verify(eventos).registrarPagamentoDaFaturaEncontrado(FATURA, recebida);
    }

    @Test
    void faturaJaPagaNaoChamaOProvedor() {
        fatura(StatusCobranca.PAGA, SituacaoProvedor.EMITIDA);

        roteador.pagarFatura(FATURA);

        verifyNoInteractions(provider, eventos);
    }

    @Test
    void faturaCanceladaE409() {
        fatura(StatusCobranca.CANCELADA, SituacaoProvedor.EMITIDA);

        assertThatThrownBy(() -> roteador.pagarFatura(FATURA)).isInstanceOf(CobrancaNaoPagavelException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void faturaAindaNaoEmitidaE400() {
        fatura(StatusCobranca.PENDENTE, SituacaoProvedor.NAO_EMITIDA);

        assertThatThrownBy(() -> roteador.pagarFatura(FATURA)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void faturaDeOutraEscolaE404() {
        when(faturaRepository.findByIdAndEscolaId(FATURA, ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roteador.pagarFatura(FATURA)).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(provider);
    }

    private void fatura(StatusCobranca status, SituacaoProvedor situacao) {
        FaturaPlataforma fatura = mock(FaturaPlataforma.class);
        when(fatura.getStatus()).thenReturn(status);
        when(fatura.getSituacaoProvedor()).thenReturn(situacao);
        when(fatura.getIdCobrancaExterna()).thenReturn("pay_fatura");
        when(faturaRepository.findByIdAndEscolaId(FATURA, ESCOLA)).thenReturn(Optional.of(fatura));
    }

    private Aluno alunoLogado() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO_ALUNO, "ALUNO", ESCOLA), null, "ROLE_ALUNO"));
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).build();
        when(alunoRepository.findByUsuarioIdAndEscolaId(USUARIO_ALUNO, ESCOLA)).thenReturn(Optional.of(aluno));
        return aluno;
    }

    private static CobrancaProvedor noProvedor(StatusCobrancaProvedor status) {
        return new CobrancaProvedor("pay_123", "EDK-B-" + COBRANCA, status, FormaPagamento.PIX,
                new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("499.01"), LocalDate.now(),
                LocalDate.now(), null, null);
    }

    private void cobranca(SituacaoProvedor situacao, Aluno dono) {
        cobranca(situacao);
        Cobranca cobranca = cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA).orElseThrow();
        when(cobranca.getAluno()).thenReturn(dono);
    }

    private void contaComChave() {
        ContaRecebimentoEscola conta = ContaRecebimentoEscola.builder().escolaId(ESCOLA).build();
        conta.setCredencialCifrada(cripto.cifrar(CHAVE_SUBCONTA));
        when(contaRepository.findByEscolaId(ESCOLA)).thenReturn(Optional.of(conta));
    }

    private void cobranca(SituacaoProvedor situacao) {
        Cobranca cobranca = mock(Cobranca.class);
        when(cobranca.getSituacaoProvedor()).thenReturn(situacao);
        when(cobranca.getIdCobrancaExterna()).thenReturn("pay_123");
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.of(cobranca));
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
