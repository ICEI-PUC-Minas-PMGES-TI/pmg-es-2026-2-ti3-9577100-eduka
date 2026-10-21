package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.pucminas.eduka.backend.domain.ClienteProvedor;
import br.pucminas.eduka.backend.domain.ContaProvedorCliente;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.LancarFaturaRequest;
import br.pucminas.eduka.backend.exception.CobrancaJaPagaException;
import br.pucminas.eduka.backend.exception.CobrancaNaoPagavelException;
import br.pucminas.eduka.backend.exception.ComprovanteIndisponivelException;
import br.pucminas.eduka.backend.exception.FaturaDuplicadaException;
import br.pucminas.eduka.backend.exception.PagamentoEmProcessamentoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.pagamento.tipos.BoletoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosPagador;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.NovaCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.PixProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.repository.ClienteProvedorRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/** Fatura da plataforma (Fluxo A): lancar, listar e cancelar (R26, R25). */
class RoteadorFaturaTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID SUPER_ADMIN = UUID.randomUUID();
    private static final UUID FATURA = UUID.randomUUID();
    private static final CredencialProvedor CHAVE_PAI = new CredencialProvedor("chave-da-conta-pai");
    private static final BigDecimal VALOR = new BigDecimal("299.90");
    /** 12h em Brasilia de 21/10/2026. */
    private static final Instant AGORA = Instant.parse("2026-10-21T15:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 21);

    private FaturaPlataformaRepository faturaRepository;
    private EscolaRepository escolaRepository;
    private ContaRecebimentoEscolaRepository contaRepository;
    private ClienteProvedorRepository clienteRepository;
    private PaymentProvider provider;
    private Escola escola;
    private FaturaPlataforma fatura;
    private RoteadorFatura roteador;

    @BeforeEach
    void setUp() {
        faturaRepository = mock(FaturaPlataformaRepository.class);
        escolaRepository = mock(EscolaRepository.class);
        provider = mock(PaymentProvider.class);
        logado(new UsuarioAutenticado(SUPER_ADMIN, "SUPER_ADMIN", null));

        escola = mock(Escola.class);
        when(escola.getId()).thenReturn(ESCOLA);
        when(escola.getNome()).thenReturn("Colegio Horizonte");
        when(escola.isAtivo()).thenReturn(true);
        fatura = FaturaPlataforma.builder().id(FATURA).escolaId(ESCOLA).competencia(LocalDate.of(2026, 11, 1))
                .descricao("Mensalidade Eduka novembro").valor(VALOR).dataVencimento(LocalDate.of(2026, 11, 10))
                .criadaPor(SUPER_ADMIN).build();
        when(escolaRepository.findById(ESCOLA)).thenReturn(Optional.of(escola));
        when(faturaRepository.findByIdAndEscolaId(FATURA, ESCOLA)).thenAnswer(i -> Optional.of(fatura));
        when(faturaRepository.saveAndFlush(any(FaturaPlataforma.class))).thenAnswer(i -> i.getArgument(0));
        when(provider.credencialDaContaPai()).thenReturn(CHAVE_PAI);

        contaRepository = mock(ContaRecebimentoEscolaRepository.class);
        clienteRepository = mock(ClienteProvedorRepository.class);
        when(escola.getCnpjCpf()).thenReturn("12.345.678/0001-90");
        when(escola.getEmail()).thenReturn("contato@horizonte.edu.br");
        when(contaRepository.findByEscolaId(ESCOLA)).thenReturn(Optional.empty());
        when(clienteRepository.findByEscolaIdAndContaAndReferenciaExterna(any(), any(), any()))
                .thenReturn(Optional.empty());

        roteador = new RoteadorFatura(faturaRepository, escolaRepository, contaRepository, clienteRepository,
                new IsolamentoEscola(), new ChamadorProvedor(provider), new TransacaoQueMarcaAtiva(),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ---- Lancar (R26, U26) ----

    @Test
    void lancaFaturaPendenteNaCompetenciaComQuemLancou() {
        FaturaPlataformaResponse resposta = roteador.lancar(ESCOLA, pedido("2026-11", LocalDate.of(2026, 11, 10)));

        ArgumentCaptor<FaturaPlataforma> gravada = ArgumentCaptor.forClass(FaturaPlataforma.class);
        verify(faturaRepository).saveAndFlush(gravada.capture());
        assertThat(gravada.getValue().getCompetencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(gravada.getValue().getCriadaPor()).isEqualTo(SUPER_ADMIN);
        assertThat(gravada.getValue().getStatus()).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(resposta.competencia()).isEqualTo("2026-11");
        assertThat(resposta.escolaNome()).isEqualTo("Colegio Horizonte");
        assertThat(resposta.acoes()).containsExactly(FaturaPlataformaResponse.CANCELAR);
        verifyNoInteractions(provider);
    }

    @Test
    void competenciaRepetidaEDuplicada() {
        when(faturaRepository.existsByEscolaIdAndCompetenciaAndStatusNot(ESCOLA, LocalDate.of(2026, 11, 1),
                StatusCobranca.CANCELADA)).thenReturn(true);

        assertThatThrownBy(() -> roteador.lancar(ESCOLA, pedido("2026-11", HOJE)))
                .isInstanceOf(FaturaDuplicadaException.class);
        verify(faturaRepository, never()).saveAndFlush(any());
    }

    @Test
    void lancamentoSimultaneoDaMesmaCompetenciaEDuplicado() {
        when(faturaRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unico"));

        assertThatThrownBy(() -> roteador.lancar(ESCOLA, pedido("2026-11", HOJE)))
                .isInstanceOf(FaturaDuplicadaException.class);
    }

    @Test
    void vencimentoPassadoERecusado() {
        assertThatThrownBy(() -> roteador.lancar(ESCOLA, pedido("2026-11", HOJE.minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(faturaRepository, never()).saveAndFlush(any());
    }

    @Test
    void escolaSuspensaNaoRecebeFatura() {
        when(escola.isAtivo()).thenReturn(false);

        assertThatThrownBy(() -> roteador.lancar(ESCOLA, pedido("2026-11", HOJE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mesInexistenteERecusado() {
        assertThatThrownBy(() -> roteador.lancar(ESCOLA, pedido("2026-13", HOJE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escolaInexistenteResponde404() {
        UUID outra = UUID.randomUUID();
        when(escolaRepository.findById(outra)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roteador.lancar(outra, pedido("2026-11", HOJE)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---- Listar ----

    @Test
    void escolaVeSuasFaturasComOPagar() {
        logado(new UsuarioAutenticado(UUID.randomUUID(), "MASTER", ESCOLA));
        when(faturaRepository.findAllByEscolaIdOrderByCompetenciaDesc(ESCOLA)).thenReturn(List.of(fatura));

        List<FaturaPlataformaResponse> faturas = roteador.listarMinhas();

        assertThat(faturas).singleElement().satisfies(f -> {
            assertThat(f.acoes()).containsExactly(FaturaPlataformaResponse.PAGAR);
            assertThat(f.situacaoPagamento()).isEqualTo("AGUARDANDO_PAGAMENTO");
        });
    }

    @Test
    void faturaPendenteVencidaApareceEmAtraso() {
        fatura.setDataVencimento(HOJE.minusDays(1));
        when(faturaRepository.findAllByEscolaIdOrderByCompetenciaDesc(ESCOLA)).thenReturn(List.of(fatura));

        assertThat(roteador.listarDaEscola(ESCOLA)).singleElement()
                .satisfies(f -> assertThat(f.status()).isEqualTo("EM_ATRASO"));
    }

    // ---- Cancelar (R26 com a regra do R25) ----

    @Test
    void faturaAindaNaoEmitidaECanceladaSemChamarOProvedor() {
        FaturaPlataformaResponse resposta = roteador.cancelar(ESCOLA, FATURA);

        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.CANCELADA);
        assertThat(fatura.getCanceladaEm()).isEqualTo(AGORA);
        assertThat(resposta.acoes()).isEmpty();
        verifyNoInteractions(provider);
    }

    @Test
    void faturaEmitidaERemovidaNoProvedorAntesDeCancelar() {
        emitida();
        when(provider.consultarCobranca(CHAVE_PAI, "pay_a1")).thenReturn(noProvedor(StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO));

        roteador.cancelar(ESCOLA, FATURA);

        verify(provider).removerCobranca(CHAVE_PAI, "pay_a1");
        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.CANCELADA);
    }

    @Test
    void faturaJaPagaNoProvedorRegistraOPagamentoERecusaOCancelamento() {
        emitida();
        when(provider.consultarCobranca(CHAVE_PAI, "pay_a1")).thenReturn(noProvedor(StatusCobrancaProvedor.RECEBIDA));

        assertThatThrownBy(() -> roteador.cancelar(ESCOLA, FATURA)).isInstanceOf(CobrancaJaPagaException.class);

        verify(provider, never()).removerCobranca(any(), any());
        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.PAGA);
        assertThat(fatura.getValorPago()).isEqualByComparingTo(VALOR);
        assertThat(fatura.getDataPagamento()).isEqualTo(HOJE);
        assertThat(fatura.getIdTransacaoExterna()).isEqualTo("tx-a1");
    }

    @Test
    void faturaPagaNaoECancelada() {
        fatura.setStatus(StatusCobranca.PAGA);

        assertThatThrownBy(() -> roteador.cancelar(ESCOLA, FATURA)).isInstanceOf(CobrancaJaPagaException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void faturaSendoEmitidaNaoECancelada() {
        fatura.setSituacaoProvedor(SituacaoProvedor.EMITINDO);

        assertThatThrownBy(() -> roteador.cancelar(ESCOLA, FATURA))
                .isInstanceOf(PagamentoEmProcessamentoException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void faturaDeOutraEscolaResponde404() {
        UUID outra = UUID.randomUUID();
        when(faturaRepository.findByIdAndEscolaId(FATURA, outra)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roteador.cancelar(outra, FATURA)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---- Pagar (MASTER, spec §8.2 versao da fatura) ----

    @Test
    void primeiroPagarCriaACobrancaNaContaPaiComAEscolaPagando() {
        comoMaster();
        when(provider.obterOuCriarCliente(any(), any())).thenReturn("cus_esc");
        when(provider.criarCobranca(any(), any())).thenReturn(noProvedor(StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO));
        when(provider.obterPix(CHAVE_PAI, "pay_a1")).thenReturn(new PixProvedor("aW1n", "000201", null));

        DadosPagamentoResponse resposta = roteador.pagar(FATURA, FormaPagamento.PIX);

        ArgumentCaptor<DadosPagador> pagador = ArgumentCaptor.forClass(DadosPagador.class);
        verify(provider).obterOuCriarCliente(org.mockito.ArgumentMatchers.eq(CHAVE_PAI), pagador.capture());
        assertThat(pagador.getValue().referenciaExterna()).isEqualTo("EDK-ESC-" + ESCOLA);
        assertThat(pagador.getValue().cpfCnpj()).isEqualTo("12345678000190");
        assertThat(pagador.getValue().email()).isEqualTo("contato@horizonte.edu.br");

        ArgumentCaptor<NovaCobrancaProvedor> nova = ArgumentCaptor.forClass(NovaCobrancaProvedor.class);
        verify(provider).criarCobranca(org.mockito.ArgumentMatchers.eq(CHAVE_PAI), nova.capture());
        assertThat(nova.getValue().referenciaExterna()).isEqualTo("EDK-A-" + FATURA);
        assertThat(nova.getValue().valor()).isEqualByComparingTo(VALOR);

        ArgumentCaptor<ClienteProvedor> cliente = ArgumentCaptor.forClass(ClienteProvedor.class);
        verify(clienteRepository).saveAndFlush(cliente.capture());
        assertThat(cliente.getValue().getConta()).isEqualTo(ContaProvedorCliente.PAI);

        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITIDA);
        assertThat(fatura.getIdCobrancaExterna()).isEqualTo("pay_a1");
        assertThat(resposta.pix().copiaECola()).isEqualTo("000201");
    }

    @Test
    void trocarAFormaAlteraAMesmaCobranca() {
        comoMaster();
        emitida();
        when(provider.obterLinhaDigitavel(CHAVE_PAI, "pay_a1")).thenReturn(new BoletoProvedor("2379", "2379", "1"));

        roteador.pagar(FATURA, FormaPagamento.BOLETO);

        verify(provider).alterarFormaEVencimento(CHAVE_PAI, "pay_a1", FormaPagamento.BOLETO,
                LocalDate.of(2026, 11, 10));
        verify(provider, never()).criarCobranca(any(), any());
        assertThat(fatura.getFormaPagamento()).isEqualTo(FormaPagamento.BOLETO);
    }

    @Test
    void criacaoInconclusivaFicaProcessando() {
        comoMaster();
        when(provider.obterOuCriarCliente(any(), any())).thenReturn("cus_esc");
        when(provider.criarCobranca(any(), any())).thenThrow(new ProvedorIndisponivelException("timeout"));

        DadosPagamentoResponse resposta = roteador.pagar(FATURA, FormaPagamento.PIX);

        assertThat(resposta.situacao()).isEqualTo("PROCESSANDO");
        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITINDO);
    }

    @Test
    void recusaDoProvedorDeixaFalhaDeEmissao() {
        comoMaster();
        when(provider.obterOuCriarCliente(any(), any())).thenReturn("cus_esc");
        when(provider.criarCobranca(any(), any())).thenThrow(new ProvedorRecusouException("valor invalido"));

        assertThatThrownBy(() -> roteador.pagar(FATURA, FormaPagamento.PIX))
                .isInstanceOf(ProvedorRecusouException.class);
        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.FALHA_EMISSAO);
    }

    @Test
    void faturaCanceladaNaoEPaga() {
        comoMaster();
        fatura.setStatus(StatusCobranca.CANCELADA);

        assertThatThrownBy(() -> roteador.pagar(FATURA, FormaPagamento.PIX))
                .isInstanceOf(CobrancaNaoPagavelException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void faturaDeOutraEscolaNaoEPagaPeloMaster() {
        logado(new UsuarioAutenticado(UUID.randomUUID(), "MASTER", UUID.randomUUID()));

        assertThatThrownBy(() -> roteador.pagar(FATURA, FormaPagamento.PIX))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void escolaSemCnpjNaoTravaAFatura() {
        comoMaster();
        when(escola.getCnpjCpf()).thenReturn(null);

        assertThatThrownBy(() -> roteador.pagar(FATURA, FormaPagamento.PIX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.NAO_EMITIDA);
    }

    // ---- Comprovante (R28 a R30) ----

    @Test
    void comprovanteDaFaturaPagaTemOEdukaComoBeneficiarioEOCnpjMascarado() {
        comoMaster();
        fatura.setStatus(StatusCobranca.PAGA);
        fatura.setValorPago(VALOR);
        fatura.setDataPagamento(HOJE);
        fatura.setFormaPagamento(FormaPagamento.PIX);
        fatura.setIdTransacaoExterna("tx-a1");

        ComprovanteResponse comprovante = roteador.comprovanteMinha(FATURA);

        assertThat(comprovante.numero()).isEqualTo(FATURA);
        assertThat(comprovante.beneficiarioNome()).isEqualTo("Edúka");
        assertThat(comprovante.pagadorNome()).isEqualTo("Colegio Horizonte");
        assertThat(comprovante.pagadorDocumentoMascarado()).isEqualTo("**.345.678/0001-**");
        assertThat(comprovante.codigoTransacao()).isEqualTo("tx-a1");
    }

    @Test
    void faturaNaoPagaNaoTemComprovante() {
        assertThatThrownBy(() -> roteador.comprovante(ESCOLA, FATURA))
                .isInstanceOf(ComprovanteIndisponivelException.class);
    }

    @Test
    void mascaraDeixaSoOsDigitosDoMeio() {
        assertThat(RoteadorFatura.mascarar("123.456.789-09")).isEqualTo("***.456.789-**");
        assertThat(RoteadorFatura.mascarar("12345678000190")).isEqualTo("**.345.678/0001-**");
        assertThat(RoteadorFatura.mascarar(null)).isNull();
    }

    // ---- Apoio ----

    private static void comoMaster() {
        logado(new UsuarioAutenticado(UUID.randomUUID(), "MASTER", ESCOLA));
    }

    private static LancarFaturaRequest pedido(String competencia, LocalDate vencimento) {
        return new LancarFaturaRequest(competencia, " Mensalidade Eduka novembro ", VALOR, vencimento);
    }

    private void emitida() {
        fatura.setSituacaoProvedor(SituacaoProvedor.EMITIDA);
        fatura.setIdCobrancaExterna("pay_a1");
        fatura.setFormaPagamento(FormaPagamento.PIX);
    }

    private static CobrancaProvedor noProvedor(StatusCobrancaProvedor status) {
        boolean paga = status == StatusCobrancaProvedor.RECEBIDA;
        return new CobrancaProvedor("pay_a1", "EDK-A-" + FATURA, status, FormaPagamento.PIX, VALOR,
                paga ? VALOR : null, null, paga ? HOJE : null, null, paga ? "tx-a1" : null, null);
    }

    private static void logado(UsuarioAutenticado usuario) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(usuario, null,
                "ROLE_" + usuario.perfil()));
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
