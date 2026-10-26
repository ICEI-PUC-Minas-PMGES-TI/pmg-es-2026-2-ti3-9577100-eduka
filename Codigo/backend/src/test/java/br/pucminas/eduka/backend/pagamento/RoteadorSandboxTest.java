package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.SandboxIndisponivelException;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.PiiEncryptionProperties;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/** Atalhos de Sandbox (NOVA-6): aprovar a conta filha e confirmar o pagamento de uma cobranca emitida. */
class RoteadorSandboxTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID COBRANCA = UUID.randomUUID();
    private static final String CHAVE_SUBCONTA = "$aact_hmlg_CHAVE_FICTICIA_DA_SUBCONTA";

    private ContaRecebimentoEscolaRepository contaRepository;
    private CobrancaRepository cobrancaRepository;
    private PaymentProvider provider;
    private PiiCryptoService cripto;
    private RoteadorSandbox roteador;

    @BeforeEach
    void setUp() {
        contaRepository = mock(ContaRecebimentoEscolaRepository.class);
        cobrancaRepository = mock(CobrancaRepository.class);
        provider = mock(PaymentProvider.class);
        cripto = new PiiCryptoService(new PiiEncryptionProperties("u28qCTox5pjdroT6PVuzUItNOUfd1K0ySdEX/Rk7gXU=", 1));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(UUID.randomUUID(), "MASTER", ESCOLA), null, "ROLE_MASTER"));

        roteador = new RoteadorSandbox(contaRepository, cobrancaRepository, new IsolamentoEscola(), cripto,
                new ChamadorProvedor(provider), new TransacaoQueMarcaAtiva());
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
