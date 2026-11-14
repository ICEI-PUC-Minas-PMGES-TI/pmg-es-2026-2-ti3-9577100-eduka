package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.escola.PlanoEscola;
import br.pucminas.eduka.backend.domain.financeiro.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.financeiro.StatusContaRecebimento;
import br.pucminas.eduka.backend.dto.financeiro.AtivarRecebimentosRequest;
import br.pucminas.eduka.backend.dto.financeiro.ContaRecebimentoResponse;
import br.pucminas.eduka.backend.dto.financeiro.DadosBancariosRequest;
import br.pucminas.eduka.backend.dto.financeiro.HabilitacaoRecebimentos;
import br.pucminas.eduka.backend.exception.financeiro.ContaRecebimentoNaoAtivavelException;
import br.pucminas.eduka.backend.pagamento.tipos.ContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosNovaConta;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoCadastral;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.TipoContaBancaria;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEmpresa;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.ContaRecebimentoEscolaRepository.ContaParaSincronizar;
import br.pucminas.eduka.backend.repository.financeiro.ContaRecebimentoEscolaRepository.ContaPorIdExterno;
import br.pucminas.eduka.backend.repository.financeiro.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.PiiEncryptionProperties;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/** Conta de recebimento: habilitacao (R11, TST-U04), ativar (spec §8.1), sincronizar (R10) e a rotina. */
class RoteadorContaTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID MASTER = UUID.randomUUID();
    private static final String CNPJ = "12345678000195";
    private static final String CHAVE_SUBCONTA = "$aact_hmlg_CHAVE_FICTICIA_DA_SUBCONTA";
    private static final Instant AGORA = Instant.parse("2026-10-22T15:00:00Z");
    private static final ContaProvedor CRIADA = new ContaProvedor("acc-1", "wallet-1",
            new CredencialProvedor(CHAVE_SUBCONTA));

    private ContaRecebimentoEscolaRepository contaRepository;
    private EscolaRepository escolaRepository;
    private PaymentProvider provider;
    private PiiCryptoService cripto;
    private AuditoriaService auditoriaService;
    private RoteadorConta roteador;

    /** A conta como esta "no banco": o que o roteador gravou por ultimo. */
    private ContaRecebimentoEscola salva;

    @BeforeEach
    void setUp() {
        contaRepository = mock(ContaRecebimentoEscolaRepository.class);
        escolaRepository = mock(EscolaRepository.class);
        provider = mock(PaymentProvider.class);
        cripto = new PiiCryptoService(new PiiEncryptionProperties("u28qCTox5pjdroT6PVuzUItNOUfd1K0ySdEX/Rk7gXU=", 1));
        logado(new UsuarioAutenticado(MASTER, "MASTER", ESCOLA));

        salva = null;
        when(contaRepository.findByEscolaId(ESCOLA)).thenAnswer(i -> Optional.ofNullable(salva));
        when(contaRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(salva));
        when(contaRepository.saveAndFlush(any(ContaRecebimentoEscola.class))).thenAnswer(i -> {
            ContaRecebimentoEscola conta = i.getArgument(0);
            if (conta.getId() == null) {
                conta.setId(UUID.randomUUID());
            }
            salva = conta;
            return conta;
        });
        when(contaRepository.gravarSituacao(anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        // O status "no banco" antes de gravar a situacao: o da conta salva, se for a mesma no provedor.
        when(contaRepository.buscarPorIdContaExterna(anyString())).thenAnswer(i -> Optional.ofNullable(salva)
                .filter(c -> i.getArgument(0).equals(c.getIdContaExterna()))
                .map(RoteadorContaTest::noBanco));
        auditoriaService = mock(AuditoriaService.class);

        roteador = new RoteadorConta(contaRepository, escolaRepository, new IsolamentoEscola(), cripto,
                auditoriaService, new GravadorSituacaoConta(contaRepository, auditoriaService),
                new ChamadorProvedor(provider), new TransacaoQueMarcaAtiva(), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ---- Habilitacao (R11) ----

    @Test
    void contaAprovadaDeEscolaAtivaEstaHabilitada() {
        escola(true);
        conta(StatusContaRecebimento.APROVADA);

        assertThat(roteador.habilitacao(ESCOLA)).isEqualTo(new HabilitacaoRecebimentos(true, null));
    }

    @ParameterizedTest
    @CsvSource({
            "CRIANDO, EM_CRIACAO",
            "VERIFICACAO_PENDENTE, EM_CRIACAO",
            "ERRO_CRIACAO, ERRO_CRIACAO",
            "AGUARDANDO_APROVACAO, AGUARDANDO_APROVACAO",
            "REJEITADA, REJEITADA",
            "CREDENCIAL_INDISPONIVEL, CREDENCIAL_INDISPONIVEL" })
    void contaNaoAprovadaDevolveOMotivo(StatusContaRecebimento status, String motivo) {
        escola(true);
        conta(status);

        assertThat(roteador.habilitacao(ESCOLA)).isEqualTo(new HabilitacaoRecebimentos(false, motivo));
    }

    @Test
    void semContaDevolveNaoIniciada() {
        escola(true);

        assertThat(roteador.habilitacao(ESCOLA)).isEqualTo(new HabilitacaoRecebimentos(false, "NAO_INICIADA"));
    }

    @Test
    void escolaInativaNaoRecebeNemComContaAprovada() {
        escola(false);
        conta(StatusContaRecebimento.APROVADA);

        assertThat(roteador.habilitacao(ESCOLA)).isEqualTo(new HabilitacaoRecebimentos(false, "ESCOLA_INATIVA"));
    }

    // ---- Consultar ----

    @Test
    void semContaMostraNaoIniciadaComOCnpjDaEscola() {
        escola(true);

        ContaRecebimentoResponse resposta = roteador.consultar();

        assertThat(resposta.status()).isEqualTo("NAO_INICIADA");
        assertThat(resposta.cnpj()).isEqualTo(CNPJ);
        assertThat(resposta.podeAtivar()).isTrue();
        assertThat(resposta.podeSincronizar()).isFalse();
    }

    // ---- Ativar (spec §8.1) ----

    @Test
    void ativarCriaAContaFilhaEGuardaAChaveSoCifrada() {
        escola(true);
        when(provider.criarConta(any())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(salva.getStatus()).isEqualTo(StatusContaRecebimento.CRIANDO);
            return CRIADA;
        });
        when(provider.consultarSituacaoConta(any())).thenReturn(situacao(SituacaoCadastral.EM_ANALISE));

        ContaRecebimentoResponse resposta = roteador.ativar(pedido());

        assertThat(salva.getStatus()).isEqualTo(StatusContaRecebimento.AGUARDANDO_APROVACAO);
        assertThat(salva.getIdContaExterna()).isEqualTo("acc-1");
        assertThat(salva.getWalletId()).isEqualTo("wallet-1");
        assertThat(salva.getCredencialCifrada()).isNotEqualTo(CHAVE_SUBCONTA.getBytes(StandardCharsets.UTF_8));
        assertThat(cripto.decifrar(salva.getCredencialCifrada())).isEqualTo(CHAVE_SUBCONTA);
        assertThat(salva.getSolicitadaEm()).isEqualTo(AGORA);
        assertThat(resposta.status()).isEqualTo("AGUARDANDO_APROVACAO");
        assertThat(resposta.podeSincronizar()).isTrue();
        verify(contaRepository).gravarSituacao("acc-1", "AGUARDANDO_APROVACAO", "EM_ANALISE", "EM_ANALISE",
                "EM_ANALISE", "EM_ANALISE", AGORA);
        verify(provider, never()).garantirChavePix(any());
    }

    @Test
    void dadosBancariosVaoAoProvedorMasSoBancoEFinalDaContaSaoGravados() {
        escola(true);
        when(provider.criarConta(any())).thenReturn(CRIADA);
        when(provider.consultarSituacaoConta(any())).thenReturn(situacao(SituacaoCadastral.EM_ANALISE));

        roteador.ativar(pedido());

        ArgumentCaptor<DadosNovaConta> enviada = ArgumentCaptor.forClass(DadosNovaConta.class);
        verify(provider).criarConta(enviada.capture());
        assertThat(enviada.getValue().cnpj()).isEqualTo(CNPJ);
        assertThat(enviada.getValue().dadosBancarios().agencia()).isEqualTo("1234");
        assertThat(enviada.getValue().dadosBancarios().conta()).isEqualTo("123456789");
        assertThat(salva.getBancoCodigo()).isEqualTo("341");
        assertThat(salva.getContaFinal()).isEqualTo("6789");
    }

    @Test
    void contaQueJaNasceAprovadaGanhaChavePix() {
        escola(true);
        when(provider.criarConta(any())).thenReturn(CRIADA);
        when(provider.consultarSituacaoConta(any())).thenReturn(situacao(SituacaoCadastral.APROVADO));

        roteador.ativar(pedido());

        verify(contaRepository).gravarSituacao("acc-1", "APROVADA", "APROVADO", "APROVADO", "APROVADO",
                "APROVADO", AGORA);
        verify(provider).garantirChavePix(new CredencialProvedor(CHAVE_SUBCONTA));
    }

    @Test
    void falhaAoLerASituacaoDepoisDeCriarNaoPerdeAConta() {
        escola(true);
        when(provider.criarConta(any())).thenReturn(CRIADA);
        when(provider.consultarSituacaoConta(any())).thenThrow(new ProvedorIndisponivelException("timeout"));

        ContaRecebimentoResponse resposta = roteador.ativar(pedido());

        assertThat(resposta.status()).isEqualTo("AGUARDANDO_APROVACAO");
        assertThat(salva.getCredencialCifrada()).isNotNull();
    }

    @Test
    void criacaoInconclusivaFicaVerificacaoPendenteEProcuraPeloCnpjNaProxima() {
        escola(true);
        when(provider.criarConta(any())).thenThrow(new ProvedorIndisponivelException("timeout"));

        ContaRecebimentoResponse resposta = roteador.ativar(pedido());

        assertThat(resposta.status()).isEqualTo("VERIFICACAO_PENDENTE");
        assertThat(resposta.podeAtivar()).isTrue();
        assertThat(resposta.ultimoErro()).isEqualTo(RoteadorConta.MENSAGEM_INCONCLUSIVA);

        // A conta tinha sido criada: achada pelo CNPJ, sem chave (P-13).
        when(provider.buscarContaPorCnpj(CNPJ)).thenReturn(Optional.of(new ContaProvedor("acc-1", "wallet-1", null)));

        ContaRecebimentoResponse segunda = roteador.ativar(pedido());

        assertThat(segunda.status()).isEqualTo("CREDENCIAL_INDISPONIVEL");
        assertThat(salva.getIdContaExterna()).isEqualTo("acc-1");
        assertThat(salva.getCredencialCifrada()).isNull();
        verify(provider).criarConta(any());
    }

    @Test
    void verificacaoPendenteSemContaNoProvedorCriaDeNovo() {
        escola(true);
        conta(StatusContaRecebimento.VERIFICACAO_PENDENTE);
        when(provider.buscarContaPorCnpj(CNPJ)).thenReturn(Optional.empty());
        when(provider.criarConta(any())).thenReturn(CRIADA);
        when(provider.consultarSituacaoConta(any())).thenReturn(situacao(SituacaoCadastral.EM_ANALISE));

        roteador.ativar(pedido());

        assertThat(salva.getStatus()).isEqualTo(StatusContaRecebimento.AGUARDANDO_APROVACAO);
        verify(provider).criarConta(any());
    }

    @Test
    void recusaDoProvedorFicaErroCriacaoComAMensagemEPodeSerReenviada() {
        escola(true);
        when(provider.criarConta(any())).thenThrow(new ProvedorRecusouException("O CNPJ informado ja esta em uso."));

        assertThatThrownBy(() -> roteador.ativar(pedido())).isInstanceOf(ProvedorRecusouException.class);

        assertThat(salva.getStatus()).isEqualTo(StatusContaRecebimento.ERRO_CRIACAO);
        assertThat(salva.getUltimoErro()).isEqualTo("O CNPJ informado ja esta em uso.");
        assertThat(roteador.consultar().podeAtivar()).isTrue();
        verify(provider, never()).buscarContaPorCnpj(any());
    }

    @Test
    void naoAtivaDuranteACriacao() {
        escola(true);
        conta(StatusContaRecebimento.CRIANDO);

        assertThatThrownBy(() -> roteador.ativar(pedido()))
                .isInstanceOfSatisfying(ContaRecebimentoNaoAtivavelException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(ContaRecebimentoNaoAtivavelException.EM_ANDAMENTO));
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @CsvSource({ "AGUARDANDO_APROVACAO", "APROVADA", "REJEITADA", "CREDENCIAL_INDISPONIVEL" })
    void naoAtivaContaJaCriada(StatusContaRecebimento status) {
        escola(true);
        conta(status);

        assertThatThrownBy(() -> roteador.ativar(pedido()))
                .isInstanceOfSatisfying(ContaRecebimentoNaoAtivavelException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(ContaRecebimentoNaoAtivavelException.JA_CRIADA));
        verifyNoInteractions(provider);
    }

    @Test
    void escolaComCpfNaoAtiva() {
        when(escolaRepository.findById(ESCOLA)).thenReturn(Optional.of(
                Escola.cadastrar("Escola", "123.456.789-09", null, null, null, PlanoEscola.values()[0], true)));

        assertThatThrownBy(() -> roteador.ativar(pedido())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void contaComMenosDeQuatroDigitosECompletadaComZeros() {
        assertThat(RoteadorConta.quatroUltimos("12")).isEqualTo("0012");
        assertThat(RoteadorConta.quatroUltimos("987654")).isEqualTo("7654");
    }

    // ---- Sincronizar (R10) ----

    @Test
    void sincronizarLeASituacaoComAChaveDecifrada() {
        escola(true);
        contaCriada(StatusContaRecebimento.AGUARDANDO_APROVACAO);
        when(provider.consultarSituacaoConta(any())).thenReturn(situacao(SituacaoCadastral.REJEITADO));

        roteador.sincronizarDaEscola();

        verify(provider).consultarSituacaoConta(new CredencialProvedor(CHAVE_SUBCONTA));
        verify(contaRepository).gravarSituacao("acc-1", "REJEITADA", "REJEITADO", "REJEITADO", "REJEITADO",
                "REJEITADO", AGORA);
    }

    @Test
    void sincronizarContaAindaNaoCriadaNaoChamaOProvedor() {
        escola(true);
        conta(StatusContaRecebimento.ERRO_CRIACAO);

        assertThat(roteador.sincronizarDaEscola().status()).isEqualTo("ERRO_CRIACAO");
        verifyNoInteractions(provider);
    }

    @Test
    void sincronizarComProvedorForaDoArPropagaOErro() {
        escola(true);
        contaCriada(StatusContaRecebimento.AGUARDANDO_APROVACAO);
        when(provider.consultarSituacaoConta(any())).thenThrow(new ProvedorIndisponivelException("timeout"));

        assertThatThrownBy(() -> roteador.sincronizarDaEscola()).isInstanceOf(ProvedorIndisponivelException.class);
    }

    // ---- Rotina ----

    @Test
    void rotinaSincronizaAsAguardandoMesmoComUmaFalhandoELiberaCriacaoAntiga() {
        SecurityContextHolder.clearContext();
        ContaParaSincronizar falha = paraSincronizar("acc-falha", "chave-falha");
        ContaParaSincronizar ok = paraSincronizar("acc-ok", "chave-ok");
        when(contaRepository.listarAguardandoAprovacao()).thenReturn(List.of(falha, ok));
        when(provider.consultarSituacaoConta(new CredencialProvedor("chave-falha")))
                .thenThrow(new ProvedorIndisponivelException("timeout"));
        when(provider.consultarSituacaoConta(new CredencialProvedor("chave-ok")))
                .thenReturn(situacao(SituacaoCadastral.APROVADO));

        int sincronizadas = roteador.sincronizarAguardando();

        assertThat(sincronizadas).isEqualTo(1);
        verify(contaRepository).liberarCriacaoSemResposta(eq(AGORA.minus(RoteadorConta.CRIACAO_SEM_RESPOSTA)),
                anyString(), eq(AGORA));
        verify(contaRepository).gravarSituacao("acc-ok", "APROVADA", "APROVADO", "APROVADO", "APROVADO",
                "APROVADO", AGORA);
        verify(provider).garantirChavePix(new CredencialProvedor("chave-ok"));
    }

    // ---- Auditoria (RF22.2) ----

    @Test
    void ativarFicaNoLogComOMasterQuePediu() {
        escola(true);
        when(provider.criarConta(any())).thenReturn(CRIADA);
        when(provider.consultarSituacaoConta(any())).thenReturn(situacao(SituacaoCadastral.PENDENTE));

        roteador.ativar(pedido());

        verify(auditoriaService).registrar(OperacaoAuditoria.ATIVAR, EntidadeAuditada.CONTA_RECEBIMENTO,
                salva.getId());
    }

    @Test
    void ativacaoRecusadaAntesDeReservarNaoGeraLog() {
        escola(true);
        conta(StatusContaRecebimento.CRIANDO);

        assertThatThrownBy(() -> roteador.ativar(pedido())).isInstanceOf(ContaRecebimentoNaoAtivavelException.class);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void situacaoQueMudaNoProvedorFicaNoLogSemAutor() {
        escola(true);
        contaCriada(StatusContaRecebimento.AGUARDANDO_APROVACAO);
        when(provider.consultarSituacaoConta(any())).thenReturn(situacao(SituacaoCadastral.APROVADO));

        roteador.sincronizarDaEscola();

        // Quem clicou foi o MASTER, mas quem aprovou foi o provedor.
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.ALTERAR_SITUACAO,
                EntidadeAuditada.CONTA_RECEBIMENTO, salva.getId(), ESCOLA);
    }

    @Test
    void sincronizarSemMudarASituacaoNaoGeraLog() {
        escola(true);
        contaCriada(StatusContaRecebimento.AGUARDANDO_APROVACAO);
        when(provider.consultarSituacaoConta(any())).thenReturn(situacao(SituacaoCadastral.EM_ANALISE));

        roteador.sincronizarDaEscola();

        verify(auditoriaService, never()).registrarDoProvedor(any(), any(), any(), any());
    }

    // ---- Apoio ----

    private static ContaPorIdExterno noBanco(ContaRecebimentoEscola conta) {
        UUID id = conta.getId();
        UUID escolaId = conta.getEscolaId();
        String status = conta.getStatus().name();
        return new ContaPorIdExterno() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public UUID getEscolaId() {
                return escolaId;
            }

            @Override
            public String getStatus() {
                return status;
            }
        };
    }

    private void escola(boolean ativa) {
        Escola escola = Escola.cadastrar("Escola", CNPJ, null, null, null, PlanoEscola.values()[0], ativa);
        when(escolaRepository.findById(ESCOLA)).thenReturn(Optional.of(escola));
    }

    private void conta(StatusContaRecebimento status) {
        salva = ContaRecebimentoEscola.builder().id(UUID.randomUUID()).escolaId(ESCOLA).status(status).build();
    }

    private void contaCriada(StatusContaRecebimento status) {
        conta(status);
        salva.setIdContaExterna("acc-1");
        salva.setCredencialCifrada(cripto.cifrar(CHAVE_SUBCONTA));
    }

    private ContaParaSincronizar paraSincronizar(String idExterna, String chave) {
        byte[] cifrada = cripto.cifrar(chave);
        return new ContaParaSincronizar() {
            @Override
            public String getIdContaExterna() {
                return idExterna;
            }

            @Override
            public byte[] getCredencialCifrada() {
                return cifrada;
            }
        };
    }

    private static SituacaoContaProvedor situacao(SituacaoCadastral todas) {
        return new SituacaoContaProvedor(todas, todas, todas, todas);
    }

    private static AtivarRecebimentosRequest pedido() {
        return new AtivarRecebimentosRequest("Escola Horizonte Ltda", "financeiro@horizonte.edu.br", "31999998888",
                TipoEmpresa.LIMITED, new BigDecimal("50000.00"), "Rua das Flores", "100", null, "Centro",
                "30110000", new DadosBancariosRequest("341", "1234", "123456789", "0", TipoContaBancaria.CORRENTE));
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
