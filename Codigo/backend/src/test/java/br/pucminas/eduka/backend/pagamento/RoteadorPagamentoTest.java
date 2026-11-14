package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.financeiro.ClienteProvedor;
import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.ContaProvedorCliente;
import br.pucminas.eduka.backend.domain.financeiro.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.financeiro.OrigemPagamento;
import br.pucminas.eduka.backend.domain.financeiro.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.financeiro.StatusContaRecebimento;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.pessoas.AlunoResponsavelId;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.financeiro.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.financeiro.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.financeiro.HabilitacaoRecebimentos;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.financeiro.BoletoNaoGeradoException;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaJaPagaException;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaNaoPagavelException;
import br.pucminas.eduka.backend.exception.financeiro.ComprovanteIndisponivelException;
import br.pucminas.eduka.backend.exception.financeiro.FormaPagamentoInvalidaException;
import br.pucminas.eduka.backend.exception.financeiro.PagadorSemCpfException;
import br.pucminas.eduka.backend.exception.financeiro.PagamentoEmProcessamentoException;
import br.pucminas.eduka.backend.exception.financeiro.RecebimentosIndisponiveisException;
import br.pucminas.eduka.backend.pagamento.tipos.BoletoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosPagador;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.NovaCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.PixProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.ClienteProvedorRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Pagar cobranca (spec §8.2, R12–R19; TST-U05 a U14). O provedor e mock, mas
 * passa pelo {@link ChamadorProvedor} real e por um gerenciador de transacao
 * que marca a transacao como ativa: qualquer chamada ao provedor dentro de
 * transacao falha o teste (spec §4.3).
 */
class RoteadorPagamentoTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID PESSOA_ALUNO = UUID.randomUUID();
    private static final UUID COBRANCA = UUID.randomUUID();
    private static final CredencialProvedor CREDENCIAL = new CredencialProvedor("chave-da-escola");
    private static final String REFERENCIA = "EDK-B-" + COBRANCA;
    private static final BigDecimal VALOR = new BigDecimal("500.00");
    /** 12h em Brasilia de 20/10/2026. */
    private static final Instant AGORA = Instant.parse("2026-10-20T15:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);
    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 10, 25);
    private static final PixProvedor PIX = new PixProvedor("aW1hZ2Vt", "00020101021226",
            OffsetDateTime.of(2027, 10, 25, 23, 59, 59, 0, ZoneOffset.ofHours(-3)));
    private static final BoletoProvedor BOLETO = new BoletoProvedor("23793381286000000000300000000401",
            "23791000000000500003381260000000000300000000", "123456");

    private CobrancaRepository cobrancaRepository;
    private AlunoRepository alunoRepository;
    private AlunoResponsavelRepository alunoResponsavelRepository;
    private PessoaRepository pessoaRepository;
    private ContaRecebimentoEscolaRepository contaRepository;
    private ClienteProvedorRepository clienteRepository;
    private PagamentoCobrancaRepository pagamentoRepository;
    private EscolaRepository escolaRepository;
    private RoteadorConta roteadorConta;
    private RoteadorEventos eventos;
    private PiiCryptoService piiCryptoService;
    private PaymentProvider provider;
    private Aluno aluno;
    private Cobranca cobranca;

    @BeforeEach
    void setUp() {
        cobrancaRepository = mock(CobrancaRepository.class);
        alunoRepository = mock(AlunoRepository.class);
        alunoResponsavelRepository = mock(AlunoResponsavelRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        contaRepository = mock(ContaRecebimentoEscolaRepository.class);
        clienteRepository = mock(ClienteProvedorRepository.class);
        pagamentoRepository = mock(PagamentoCobrancaRepository.class);
        escolaRepository = mock(EscolaRepository.class);
        roteadorConta = mock(RoteadorConta.class);
        eventos = mock(RoteadorEventos.class);
        piiCryptoService = mock(PiiCryptoService.class);
        provider = mock(PaymentProvider.class);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "ALUNO", ESCOLA), null, "ROLE_ALUNO"));

        aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(PESSOA_ALUNO).usuarioId(USUARIO)
                .matricula("2026-001").build();
        cobranca = Cobranca.builder().id(COBRANCA).escolaId(ESCOLA).aluno(aluno).descricao("Mensalidade outubro")
                .valor(VALOR).dataVencimento(VENCIMENTO).status(StatusCobranca.PENDENTE).build();
        when(alunoRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.of(aluno));
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenAnswer(i -> Optional.of(cobranca));
        when(cobrancaRepository.saveAndFlush(any(Cobranca.class))).thenAnswer(i -> i.getArgument(0));
        when(clienteRepository.saveAndFlush(any(ClienteProvedor.class))).thenAnswer(i -> i.getArgument(0));

        when(roteadorConta.habilitacao(ESCOLA)).thenReturn(new HabilitacaoRecebimentos(true, null));
        byte[] credencialCifrada = { 1, 2, 3 };
        when(contaRepository.findByEscolaId(ESCOLA)).thenReturn(Optional.of(ContaRecebimentoEscola.builder()
                .escolaId(ESCOLA).status(StatusContaRecebimento.APROVADA).credencialCifrada(credencialCifrada)
                .build()));
        when(piiCryptoService.decifrar(credencialCifrada)).thenReturn(CREDENCIAL.valor());

        when(pessoaRepository.findByIdAndEscolaId(PESSOA_ALUNO, ESCOLA))
                .thenReturn(Optional.of(pessoa(PESSOA_ALUNO, "Ana Aluna", "52998224725")));
        when(alunoResponsavelRepository.findById_AlunoIdAndFinanceiroTrue(aluno.getId())).thenReturn(Optional.empty());
        when(clienteRepository.findByEscolaIdAndContaAndReferenciaExterna(any(), any(), any()))
                .thenReturn(Optional.empty());

        when(provider.obterOuCriarCliente(eq(CREDENCIAL), any())).thenReturn("cus_1");
        when(provider.criarCobranca(eq(CREDENCIAL), any())).thenReturn(cobrancaNoProvedor("pay_1"));
        when(provider.obterPix(CREDENCIAL, "pay_1")).thenReturn(PIX);
        when(provider.obterLinhaDigitavel(CREDENCIAL, "pay_1")).thenReturn(BOLETO);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ---- U05: primeira vez cria uma cobranca no provedor ----

    @Test
    void naoEmitidaCriaUmaCobrancaComFormaReferenciaEVencimento() {
        DadosPagamentoResponse dados = roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        ArgumentCaptor<NovaCobrancaProvedor> nova = ArgumentCaptor.forClass(NovaCobrancaProvedor.class);
        verify(provider).criarCobranca(eq(CREDENCIAL), nova.capture());
        assertThat(nova.getValue()).isEqualTo(new NovaCobrancaProvedor("cus_1", FormaPagamento.PIX, VALOR,
                VENCIMENTO, "Mensalidade outubro", REFERENCIA));

        assertThat(cobranca.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITIDA);
        assertThat(cobranca.getIdCobrancaExterna()).isEqualTo("pay_1");
        assertThat(cobranca.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(cobranca.getEmissaoIniciadaEm()).isEqualTo(AGORA);
        assertThat(cobranca.getEmitidaEm()).isEqualTo(AGORA);

        assertThat(dados.situacao()).isEqualTo(DadosPagamentoResponse.PRONTO);
        assertThat(dados.forma()).isEqualTo(FormaPagamento.PIX);
        assertThat(dados.valor()).isEqualTo(VALOR);
        assertThat(dados.vencimento()).isEqualTo(VENCIMENTO);
        assertThat(dados.pix().qrCodeBase64()).isEqualTo(PIX.qrCodeBase64());
        assertThat(dados.pix().copiaECola()).isEqualTo(PIX.copiaECola());
        assertThat(dados.pix().validade()).isEqualTo(PIX.validade());
        assertThat(dados.boleto()).isNull();
    }

    @Test
    void osDadosDePagamentoDizemQueEscolaRecebe() {
        Escola escola = mock(Escola.class);
        when(escola.getNome()).thenReturn("Escola Aurora");
        when(escolaRepository.findById(ESCOLA)).thenReturn(Optional.of(escola));

        DadosPagamentoResponse dados = roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.BOLETO);

        assertThat(dados.recebedorNome()).isEqualTo("Escola Aurora");
    }

    @Test
    void travaEmEmitindoAntesDeChamarOProvedor() {
        SituacaoProvedor[] situacaoNaChamada = new SituacaoProvedor[1];
        when(provider.criarCobranca(eq(CREDENCIAL), any())).thenAnswer(i -> {
            situacaoNaChamada[0] = cobranca.getSituacaoProvedor();
            return cobrancaNoProvedor("pay_1");
        });

        roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        InOrder ordem = inOrder(cobrancaRepository, provider);
        ordem.verify(cobrancaRepository).saveAndFlush(cobranca);
        ordem.verify(provider).criarCobranca(eq(CREDENCIAL), any());
        assertThat(situacaoNaChamada[0]).isEqualTo(SituacaoProvedor.EMITINDO);
    }

    @Test
    void boletoDevolveALinhaDigitavelSemPix() {
        DadosPagamentoResponse dados = roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.BOLETO);

        assertThat(dados.forma()).isEqualTo(FormaPagamento.BOLETO);
        assertThat(dados.boleto().linhaDigitavel()).isEqualTo(BOLETO.linhaDigitavel());
        assertThat(dados.boleto().codigoBarras()).isEqualTo(BOLETO.codigoBarras());
        assertThat(dados.pix()).isNull();
        verify(provider, never()).obterPix(any(), any());
    }

    @Test
    void falhaEmissaoAnteriorTentaDeNovo() {
        cobranca.setSituacaoProvedor(SituacaoProvedor.FALHA_EMISSAO);
        cobranca.setUltimoErroProvedor("recusa anterior");

        roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        verify(provider).criarCobranca(eq(CREDENCIAL), any());
        assertThat(cobranca.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITIDA);
        assertThat(cobranca.getUltimoErroProvedor()).isNull();
    }

    // ---- U06 e U07: cobranca ja emitida e reaproveitada ----

    @Test
    void emitidaComAMesmaFormaNaoCriaNemAlteraEBuscaOPix() {
        emitida(FormaPagamento.PIX);

        DadosPagamentoResponse dados = roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        verify(provider, never()).criarCobranca(any(), any());
        verify(provider, never()).alterarFormaEVencimento(any(), any(), any(), any());
        verify(provider).obterPix(CREDENCIAL, "pay_1");
        assertThat(dados.pix().copiaECola()).isEqualTo(PIX.copiaECola());
    }

    @Test
    void emitidaComOutraFormaAlteraAMesmaCobranca() {
        emitida(FormaPagamento.PIX);

        DadosPagamentoResponse dados = roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.BOLETO);

        verify(provider, never()).criarCobranca(any(), any());
        verify(provider).alterarFormaEVencimento(CREDENCIAL, "pay_1", FormaPagamento.BOLETO, VENCIMENTO);
        verify(provider).obterLinhaDigitavel(CREDENCIAL, "pay_1");
        assertThat(cobranca.getFormaPagamento()).isEqualTo(FormaPagamento.BOLETO);
        assertThat(cobranca.getIdCobrancaExterna()).isEqualTo("pay_1");
        assertThat(dados.boleto().linhaDigitavel()).isEqualTo(BOLETO.linhaDigitavel());
    }

    // ---- U08: vencimento enviado = max(vencimento, hoje) ----

    @Test
    void cobrancaEmAtrasoEnviaVencimentoHojeNoFusoDaEscola() {
        // 23h30 de 19/10 em Brasilia; em UTC ja e dia 20.
        Instant noiteDeBrasilia = Instant.parse("2026-10-20T02:30:00Z");
        cobranca.setDataVencimento(LocalDate.of(2026, 10, 10));
        cobranca.setStatus(StatusCobranca.EM_ATRASO);

        DadosPagamentoResponse dados = roteador(noiteDeBrasilia).pagarCobranca(COBRANCA, FormaPagamento.BOLETO);

        ArgumentCaptor<NovaCobrancaProvedor> nova = ArgumentCaptor.forClass(NovaCobrancaProvedor.class);
        verify(provider).criarCobranca(eq(CREDENCIAL), nova.capture());
        assertThat(nova.getValue().vencimento()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(dados.vencimento()).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void emitidaQueVenceuAtualizaOVencimentoMesmoSemTrocarAForma() {
        emitida(FormaPagamento.BOLETO);
        cobranca.setDataVencimento(LocalDate.of(2026, 10, 10));
        cobranca.setStatus(StatusCobranca.EM_ATRASO);

        roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.BOLETO);

        verify(provider).alterarFormaEVencimento(CREDENCIAL, "pay_1", FormaPagamento.BOLETO, HOJE);
        verify(provider, never()).criarCobranca(any(), any());
    }

    // ---- U09: inconclusivo na criacao ----

    @Test
    void inconclusivoNaCriacaoDeixaEmitindoEDevolveProcessando() {
        when(provider.criarCobranca(eq(CREDENCIAL), any())).thenThrow(new ProvedorIndisponivelException("timeout"));

        DadosPagamentoResponse dados = roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        assertThat(dados.situacao()).isEqualTo(DadosPagamentoResponse.PROCESSANDO);
        assertThat(dados.pix()).isNull();
        assertThat(dados.boleto()).isNull();
        assertThat(cobranca.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITINDO);
        verify(provider, never()).obterPix(any(), any());
    }

    @Test
    void emitindoRecusaNovoCliqueSemChamarOProvedor() {
        cobranca.setSituacaoProvedor(SituacaoProvedor.EMITINDO);

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(PagamentoEmProcessamentoException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void doisCliquesAoMesmoTempoUmPerdeATravaENaoChamaOProvedor() {
        when(cobrancaRepository.saveAndFlush(any(Cobranca.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Cobranca.class, COBRANCA));

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(PagamentoEmProcessamentoException.class);
        verifyNoInteractions(provider);
    }

    // ---- U10: recusa ----

    @Test
    void recusaDeixaFalhaEmissaoComAMensagemELanca() {
        when(provider.criarCobranca(eq(CREDENCIAL), any()))
                .thenThrow(new ProvedorRecusouException("O valor da cobranca deve ser maior que R$ 5,00"));

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(ProvedorRecusouException.class);

        assertThat(cobranca.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.FALHA_EMISSAO);
        assertThat(cobranca.getUltimoErroProvedor())
                .isEqualTo("O valor da cobranca deve ser maior que R$ 5,00")
                .doesNotContain("52998224725");
    }

    @Test
    void falhaAoCadastrarOPagadorLiberaParaTentarDeNovo() {
        when(provider.obterOuCriarCliente(eq(CREDENCIAL), any()))
                .thenThrow(new ProvedorIndisponivelException("timeout"));

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(ProvedorIndisponivelException.class);

        assertThat(cobranca.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.FALHA_EMISSAO);
        verify(provider, never()).criarCobranca(any(), any());
    }

    // ---- U11 e U12: posse, situacao e habilitacao antes de qualquer chamada ----

    @Test
    void cobrancaDeOutroAlunoNaoEncontradaSemChamarOProvedor() {
        Aluno outro = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(UUID.randomUUID()).build();
        cobranca.setAluno(outro);

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(provider);
        verify(cobrancaRepository, never()).saveAndFlush(any());
    }

    @Test
    void cobrancaDeOutraEscolaNaoEncontrada() {
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void escolaNaoHabilitadaRecusaSemChamarOProvedor() {
        when(roteadorConta.habilitacao(ESCOLA)).thenReturn(new HabilitacaoRecebimentos(false, "AGUARDANDO_APROVACAO"));

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(RecebimentosIndisponiveisException.class)
                .hasMessageContaining("AGUARDANDO_APROVACAO");
        verifyNoInteractions(provider);
        assertThat(cobranca.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.NAO_EMITIDA);
    }

    @ParameterizedTest
    @EnumSource(value = StatusCobranca.class, names = { "PAGA", "CANCELADA" })
    void cobrancaPagaOuCanceladaNaoEPagavel(StatusCobranca status) {
        cobranca.setStatus(status);

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(CobrancaNaoPagavelException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void formaAusenteEInvalida() {
        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, null))
                .isInstanceOf(FormaPagamentoInvalidaException.class);
        verifyNoInteractions(provider, cobrancaRepository);
    }

    // ---- U13: escolha do pagador ----

    @Test
    void pagadorEOResponsavelInformadoNaCobranca() {
        UUID responsavel = UUID.randomUUID();
        cobranca.setResponsavelId(responsavel);
        when(pessoaRepository.findByIdAndEscolaId(responsavel, ESCOLA))
                .thenReturn(Optional.of(pessoa(responsavel, "Rita Responsavel", "11144477735")));
        financeiro(UUID.randomUUID());

        roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        assertThat(pagadorEnviado()).isEqualTo(
                new DadosPagador("EDK-PES-" + responsavel, "Rita Responsavel", "11144477735", "pessoa@exemplo.com"));
    }

    @Test
    void semResponsavelNaCobrancaPagaOResponsavelFinanceiro() {
        UUID financeiro = UUID.randomUUID();
        financeiro(financeiro);
        when(pessoaRepository.findByIdAndEscolaId(financeiro, ESCOLA))
                .thenReturn(Optional.of(pessoa(financeiro, "Fabio Financeiro", "11144477735")));

        roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        assertThat(pagadorEnviado().referenciaExterna()).isEqualTo("EDK-PES-" + financeiro);
        assertThat(pagadorEnviado().cpfCnpj()).isEqualTo("11144477735");
    }

    @Test
    void semResponsavelPagaOProprioAluno() {
        roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        assertThat(pagadorEnviado()).isEqualTo(
                new DadosPagador("EDK-PES-" + PESSOA_ALUNO, "Ana Aluna", "52998224725", "pessoa@exemplo.com"));
    }

    @Test
    void pagadorSemCpfValidoRecusaSemTravarNemChamarOProvedor() {
        when(pessoaRepository.findByIdAndEscolaId(PESSOA_ALUNO, ESCOLA))
                .thenReturn(Optional.of(pessoa(PESSOA_ALUNO, "Ana Aluna", null)));

        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(PagadorSemCpfException.class);

        when(pessoaRepository.findByIdAndEscolaId(PESSOA_ALUNO, ESCOLA))
                .thenReturn(Optional.of(pessoa(PESSOA_ALUNO, "Ana Aluna", "12345678900")));
        assertThatThrownBy(() -> roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .isInstanceOf(PagadorSemCpfException.class);

        verifyNoInteractions(provider);
        assertThat(cobranca.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.NAO_EMITIDA);
    }

    // ---- U14: cliente cadastrado uma vez por pagador ----

    @Test
    void clienteJaCadastradoNaoEPedidoDeNovo() {
        when(clienteRepository.findByEscolaIdAndContaAndReferenciaExterna(ESCOLA, ContaProvedorCliente.FILHA,
                "EDK-PES-" + PESSOA_ALUNO))
                .thenReturn(Optional.of(ClienteProvedor.builder().idClienteExterno("cus_antigo").build()));

        roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        verify(provider, never()).obterOuCriarCliente(any(), any());
        ArgumentCaptor<NovaCobrancaProvedor> nova = ArgumentCaptor.forClass(NovaCobrancaProvedor.class);
        verify(provider).criarCobranca(eq(CREDENCIAL), nova.capture());
        assertThat(nova.getValue().idCliente()).isEqualTo("cus_antigo");
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void clienteNovoEGuardadoNaContaFilha() {
        roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        ArgumentCaptor<ClienteProvedor> cliente = ArgumentCaptor.forClass(ClienteProvedor.class);
        verify(clienteRepository).saveAndFlush(cliente.capture());
        assertThat(cliente.getValue().getEscolaId()).isEqualTo(ESCOLA);
        assertThat(cliente.getValue().getConta()).isEqualTo(ContaProvedorCliente.FILHA);
        assertThat(cliente.getValue().getReferenciaExterna()).isEqualTo("EDK-PES-" + PESSOA_ALUNO);
        assertThat(cliente.getValue().getIdClienteExterno()).isEqualTo("cus_1");
    }

    @Test
    void clienteGravadoPorOutroPedidoAoMesmoTempoNaoImpedeOPagamento() {
        when(clienteRepository.saveAndFlush(any(ClienteProvedor.class)))
                .thenThrow(new DataIntegrityViolationException("uk_cliente_provedor"));

        DadosPagamentoResponse dados = roteador(AGORA).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        assertThat(dados.situacao()).isEqualTo(DadosPagamentoResponse.PRONTO);
        assertThat(cobranca.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITIDA);
    }

    // ---- Cancelar: remove antes no provedor (spec §8.5) ----

    @ParameterizedTest
    @EnumSource(value = SituacaoProvedor.class, names = { "NAO_EMITIDA", "FALHA_EMISSAO" })
    void cancelarSemCobrancaNoProvedorNaoChamaOProvedor(SituacaoProvedor situacao) {
        cobranca.setSituacaoProvedor(situacao);

        roteador(AGORA).cancelarNoProvedor(cobranca);

        verifyNoInteractions(provider);
    }

    @Test
    void cancelarEmitindoRecusaSemChamarOProvedor() {
        cobranca.setSituacaoProvedor(SituacaoProvedor.EMITINDO);

        assertThatThrownBy(() -> roteador(AGORA).cancelarNoProvedor(cobranca))
                .isInstanceOf(PagamentoEmProcessamentoException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void cancelarEmitidaEmAbertoConfereERemoveNoProvedor() {
        emitida(FormaPagamento.PIX);
        when(provider.consultarCobranca(CREDENCIAL, "pay_1")).thenReturn(cobrancaNoProvedor("pay_1"));

        roteador(AGORA).cancelarNoProvedor(cobranca);

        InOrder ordem = inOrder(provider);
        ordem.verify(provider).consultarCobranca(CREDENCIAL, "pay_1");
        ordem.verify(provider).removerCobranca(CREDENCIAL, "pay_1");
        verifyNoInteractions(eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusCobrancaProvedor.class, names = { "CONFIRMADA", "RECEBIDA" })
    void cancelarJaPagaNoProvedorRegistraOPagamentoERecusa(StatusCobrancaProvedor status) {
        emitida(FormaPagamento.PIX);
        CobrancaProvedor paga = new CobrancaProvedor("pay_1", REFERENCIA, status, FormaPagamento.PIX, VALOR, VALOR,
                null, HOJE, null, "tr_1", null);
        when(provider.consultarCobranca(CREDENCIAL, "pay_1")).thenReturn(paga);

        assertThatThrownBy(() -> roteador(AGORA).cancelarNoProvedor(cobranca))
                .isInstanceOf(CobrancaJaPagaException.class);
        verify(eventos).registrarPagamentoEncontrado(COBRANCA, paga);
        verify(provider, never()).removerCobranca(any(), any());
    }

    // ---- Comprovante (R28 a R30) ----

    @Test
    void comprovanteDoAlunoTrazOPagamentoAEscolaEOPagadorMascarado() {
        pagaOnline();

        ComprovanteResponse comprovante = roteador(AGORA).comprovante(COBRANCA);

        assertThat(comprovante.numero()).isEqualTo(COBRANCA);
        assertThat(comprovante.beneficiarioNome()).isEqualTo("Escola Aurora");
        assertThat(comprovante.beneficiarioDocumento()).isEqualTo("12.345.678/0001-90");
        assertThat(comprovante.pagadorNome()).isEqualTo("Ana Aluna");
        assertThat(comprovante.pagadorDocumentoMascarado()).isEqualTo("***.982.247-**");
        assertThat(comprovante.descricao()).isEqualTo("Mensalidade outubro");
        assertThat(comprovante.vencimento()).isEqualTo(VENCIMENTO);
        assertThat(comprovante.valorPago()).isEqualByComparingTo(VALOR);
        assertThat(comprovante.dataPagamento()).isEqualTo(HOJE);
        assertThat(comprovante.forma()).isEqualTo(FormaPagamento.PIX);
        assertThat(comprovante.codigoTransacao()).isEqualTo("tr_1");
    }

    @Test
    void comprovanteSaiNoNomeDoResponsavelFinanceiro() {
        pagaOnline();
        UUID mae = UUID.randomUUID();
        financeiro(mae);
        when(pessoaRepository.findByIdAndEscolaId(mae, ESCOLA))
                .thenReturn(Optional.of(pessoa(mae, "Marta Mae", "11144477735")));

        ComprovanteResponse comprovante = roteador(AGORA).comprovante(COBRANCA);

        assertThat(comprovante.pagadorNome()).isEqualTo("Marta Mae");
        assertThat(comprovante.pagadorDocumentoMascarado()).isEqualTo("***.444.777-**");
    }

    @Test
    void financeiroDaEscolaVeOComprovanteDeQualquerAluno() {
        pagaOnline();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(UUID.randomUUID(), "FINANCEIRO", ESCOLA), null, "ROLE_FINANCEIRO"));

        assertThat(roteador(AGORA).comprovante(COBRANCA).numero()).isEqualTo(COBRANCA);
        verify(alunoRepository, never()).findByUsuarioIdAndEscolaId(any(), any());
    }

    @Test
    void comprovanteDeCobrancaDeOutroAlunoResponde404() {
        pagaOnline();
        when(alunoRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.of(
                Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> roteador(AGORA).comprovante(COBRANCA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(pagamentoRepository);
    }

    @Test
    void semPagamentoConfirmadoNaoHaComprovante() {
        assertThatThrownBy(() -> roteador(AGORA).comprovante(COBRANCA))
                .isInstanceOf(ComprovanteIndisponivelException.class);
        verifyNoInteractions(pagamentoRepository);
    }

    @Test
    void baixaManualNaoTemComprovante() {
        cobranca.setStatus(StatusCobranca.PAGA);
        when(pagamentoRepository.findByEscolaIdAndCobrancaId(ESCOLA, COBRANCA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roteador(AGORA).comprovante(COBRANCA))
                .isInstanceOf(ComprovanteIndisponivelException.class);
    }

    @Test
    void escolaCadastradaComCpfSaiMascarada() {
        assertThat(RoteadorPagamento.documentoDoBeneficiario("52998224725")).isEqualTo("***.982.247-**");
        assertThat(RoteadorPagamento.documentoDoBeneficiario(null)).isNull();
    }

    // ---- apoio ----

    private void pagaOnline() {
        emitida(FormaPagamento.PIX);
        cobranca.setStatus(StatusCobranca.PAGA);
        when(pagamentoRepository.findByEscolaIdAndCobrancaId(ESCOLA, COBRANCA)).thenReturn(Optional.of(
                PagamentoCobranca.builder().escolaId(ESCOLA).cobrancaId(COBRANCA).valorPago(VALOR).dataPagamento(HOJE)
                        .formaPagamento(FormaPagamento.PIX).origem(OrigemPagamento.ONLINE).idTransacaoExterna("tr_1")
                        .build()));
        Escola escola = mock(Escola.class);
        when(escola.getNome()).thenReturn("Escola Aurora");
        when(escola.getCnpjCpf()).thenReturn("12345678000190");
        when(escolaRepository.findById(ESCOLA)).thenReturn(Optional.of(escola));
    }

    private RoteadorPagamento roteador(Instant agora) {
        return new RoteadorPagamento(cobrancaRepository, alunoRepository, alunoResponsavelRepository,
                pessoaRepository, contaRepository, clienteRepository, pagamentoRepository, escolaRepository, roteadorConta,
                eventos, new IsolamentoEscola(), piiCryptoService, new ChamadorProvedor(provider), new TransacaoQueMarcaAtiva(),
                Clock.fixed(agora, ZoneOffset.UTC));
    }

    private void emitida(FormaPagamento forma) {
        cobranca.setSituacaoProvedor(SituacaoProvedor.EMITIDA);
        cobranca.setIdCobrancaExterna("pay_1");
        cobranca.setFormaPagamento(forma);
    }

    private void financeiro(UUID pessoaId) {
        when(alunoResponsavelRepository.findById_AlunoIdAndFinanceiroTrue(aluno.getId()))
                .thenReturn(Optional.of(new AlunoResponsavel(new AlunoResponsavelId(aluno.getId(), pessoaId),
                        "Mae", true)));
    }

    private DadosPagador pagadorEnviado() {
        ArgumentCaptor<DadosPagador> pagador = ArgumentCaptor.forClass(DadosPagador.class);
        verify(provider).obterOuCriarCliente(eq(CREDENCIAL), pagador.capture());
        return pagador.getValue();
    }

    private static Pessoa pessoa(UUID id, String nome, String cpf) {
        return Pessoa.builder().id(id).escolaId(ESCOLA).nome(nome).cpf(cpf).email("pessoa@exemplo.com").ativo(true)
                .build();
    }

    private static CobrancaProvedor cobrancaNoProvedor(String id) {
        return new CobrancaProvedor(id, REFERENCIA, StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO,
                FormaPagamento.PIX, VALOR, null, null, null, null, null, null);
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

    // ---- PDF do boleto ----

    @Test
    void baixarBoletoEmitidoChamaOProvedorComACredencialDaEscola() {
        emitida(FormaPagamento.BOLETO);
        byte[] pdf = { '%', 'P', 'D', 'F' };
        when(provider.baixarBoletoPdf(CREDENCIAL, "pay_1")).thenReturn(pdf);

        assertThat(roteador(AGORA).baixarBoleto(COBRANCA)).isEqualTo(pdf);
    }

    @Test
    void boletoNaoGeradoOuEmitidoComoPixERecusadoSemChamarOProvedor() {
        assertThatThrownBy(() -> roteador(AGORA).baixarBoleto(COBRANCA))
                .isInstanceOf(BoletoNaoGeradoException.class);

        emitida(FormaPagamento.PIX);
        assertThatThrownBy(() -> roteador(AGORA).baixarBoleto(COBRANCA))
                .isInstanceOf(BoletoNaoGeradoException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void boletoDeCobrancaCanceladaERecusado() {
        emitida(FormaPagamento.BOLETO);
        cobranca.setStatus(StatusCobranca.CANCELADA);

        assertThatThrownBy(() -> roteador(AGORA).baixarBoleto(COBRANCA))
                .isInstanceOf(CobrancaNaoPagavelException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void boletoDeCobrancaPagaContinuaBaixavel() {
        emitida(FormaPagamento.BOLETO);
        cobranca.setStatus(StatusCobranca.PAGA);
        when(provider.baixarBoletoPdf(CREDENCIAL, "pay_1")).thenReturn(new byte[] { '%', 'P', 'D', 'F' });

        assertThat(roteador(AGORA).baixarBoleto(COBRANCA)).isNotEmpty();
    }

    @Test
    void boletoDeOutroAlunoNaoEncontrado() {
        emitida(FormaPagamento.BOLETO);
        cobranca.setAluno(Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(UUID.randomUUID()).build());

        assertThatThrownBy(() -> roteador(AGORA).baixarBoleto(COBRANCA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(provider);
    }
}
