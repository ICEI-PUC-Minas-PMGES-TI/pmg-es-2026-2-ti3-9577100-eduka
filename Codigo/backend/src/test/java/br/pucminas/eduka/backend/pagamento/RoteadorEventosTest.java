package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.StatusEventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoCadastral;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository.ContaPorIdExterno;
import br.pucminas.eduka.backend.repository.EstornoCobrancaRepository;
import br.pucminas.eduka.backend.repository.EventoProvedorRepository;
import br.pucminas.eduka.backend.repository.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.service.AuditoriaService;
import br.pucminas.eduka.backend.service.AvisoCobranca;
import br.pucminas.eduka.backend.service.AvisoCobrancaService;
import br.pucminas.eduka.backend.service.CobrancaParaAviso;

/** Gravar e aplicar avisos do provedor: uma vez so, na escola da cobranca e da conta. */
@ExtendWith(MockitoExtension.class)
class RoteadorEventosTest {

    private static final Instant AGORA = Instant.parse("2026-10-19T14:42:00Z");
    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID OUTRA_ESCOLA = UUID.randomUUID();
    private static final UUID COBRANCA = UUID.randomUUID();
    private static final UUID EVENTO = UUID.randomUUID();
    private static final String CONTA_ESCOLA = "3d134583-fe86-4aad-800f-6c6e63629478";
    private static final String COBRANCA_EXTERNA = "pay_mfqkh82qhahsoakx";
    private static final UUID FATURA = UUID.randomUUID();
    private static final String CONTA_PAI = "conta-pai-eduka";

    @Mock
    private EventoProvedorRepository eventoRepository;
    @Mock
    private CobrancaRepository cobrancaRepository;
    @Mock
    private ContaRecebimentoEscolaRepository contaRepository;
    @Mock
    private PagamentoCobrancaRepository pagamentoRepository;
    @Mock
    private EstornoCobrancaRepository estornoRepository;
    @Mock
    private FaturaPlataformaRepository faturaRepository;
    @Mock
    private AvisoCobrancaService avisoCobrancaService;
    @Mock
    private AuditoriaService auditoriaService;

    private RoteadorEventos roteador;
    private br.pucminas.eduka.backend.domain.EventoProvedor registro;

    @BeforeEach
    void setUp() {
        roteador = new RoteadorEventos(eventoRepository, cobrancaRepository, contaRepository, pagamentoRepository,
                estornoRepository, faturaRepository, avisoCobrancaService, auditoriaService,
                new GravadorSituacaoConta(contaRepository, auditoriaService), Clock.fixed(AGORA, ZoneOffset.UTC));
        registro = br.pucminas.eduka.backend.domain.EventoProvedor.builder()
                .id(EVENTO)
                .idEventoExterno("evt_1&1")
                .tipoOriginal("PAYMENT_RECEIVED")
                .tipo(TipoEventoProvedor.PAGAMENTO_RECEBIDO)
                .payload("{}")
                .build();
    }

    // ---- Pendentes para o agendador (NOVA-4) ----

    @Test
    void pendentesSaoOsQueNaoTerminaramComTentativasSobrandoEForaDaJanelaDoWebhook() {
        UUID pendente = UUID.randomUUID();
        when(eventoRepository.buscarPendentes(EnumSet.of(StatusEventoProvedor.RECEBIDO,
                StatusEventoProvedor.PROCESSANDO, StatusEventoProvedor.ERRO), 10,
                AGORA.minus(RoteadorEventos.ESPERA_ANTES_DE_REPROCESSAR), PageRequest.of(0, 50)))
                .thenReturn(List.of(pendente));

        assertThat(roteador.pendentes(10, 50)).containsExactly(pendente);
    }

    // ---- Gravar ----

    @Test
    void gravaOAvisoNovoComoRecebido() {
        when(eventoRepository.existsByIdEventoExterno("evt_1&1")).thenReturn(false);
        when(eventoRepository.saveAndFlush(any())).thenAnswer(i -> {
            br.pucminas.eduka.backend.domain.EventoProvedor salvo = i.getArgument(0);
            salvo.setId(EVENTO);
            return salvo;
        });

        assertThat(roteador.gravar(pagamentoRecebido(CONTA_ESCOLA))).contains(EVENTO);

        ArgumentCaptor<br.pucminas.eduka.backend.domain.EventoProvedor> gravado = ArgumentCaptor
                .forClass(br.pucminas.eduka.backend.domain.EventoProvedor.class);
        verify(eventoRepository).saveAndFlush(gravado.capture());
        assertThat(gravado.getValue().getStatus()).isEqualTo(StatusEventoProvedor.RECEBIDO);
        assertThat(gravado.getValue().getReferenciaExterna()).isEqualTo("EDK-B-" + COBRANCA);
        assertThat(gravado.getValue().getIdContaExterna()).isEqualTo(CONTA_ESCOLA);
        assertThat(gravado.getValue().getEscolaId()).isNull();
    }

    @Test
    void avisoRepetidoNaoEGravadoDeNovo() {
        when(eventoRepository.existsByIdEventoExterno("evt_1&1")).thenReturn(true);

        assertThat(roteador.gravar(pagamentoRecebido(CONTA_ESCOLA))).isEmpty();
        verify(eventoRepository, never()).saveAndFlush(any());
    }

    // ---- Aplicar pagamento ----

    @Test
    void pagamentoRecebidoRegistraPagamentoOnlineEMarcaCobrancaComoPaga() {
        cobrancaEncontradaPeloIdExterno("PENDENTE");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.marcarPagaPeloProvedor(COBRANCA, AGORA)).thenReturn(1);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        ArgumentCaptor<PagamentoCobranca> pagamento = ArgumentCaptor.forClass(PagamentoCobranca.class);
        verify(pagamentoRepository).save(pagamento.capture());
        assertThat(pagamento.getValue().getEscolaId()).isEqualTo(ESCOLA);
        assertThat(pagamento.getValue().getCobrancaId()).isEqualTo(COBRANCA);
        assertThat(pagamento.getValue().getOrigem()).isEqualTo(OrigemPagamento.ONLINE);
        assertThat(pagamento.getValue().getValorPago()).isEqualByComparingTo("150.00");
        assertThat(pagamento.getValue().getValorLiquido()).isEqualByComparingTo("149.01");
        assertThat(pagamento.getValue().getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(pagamento.getValue().getDataPagamento()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(pagamento.getValue().getIdTransacaoExterna()).isEqualTo("tx-pix-1");
        assertThat(pagamento.getValue().getEventoId()).isEqualTo(EVENTO);

        ArgumentCaptor<CobrancaParaAviso> aviso = ArgumentCaptor.forClass(CobrancaParaAviso.class);
        verify(avisoCobrancaService).publicar(eq(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO), aviso.capture());
        assertThat(aviso.getValue().id()).isEqualTo(COBRANCA);
        assertThat(aviso.getValue().pagaEm()).isEqualTo(AGORA);

        // Sem usuario no webhook: auditado como do provedor, na escola da cobranca.
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO,
                EntidadeAuditada.COBRANCA, COBRANCA, ESCOLA);

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(registro.getEscolaId()).isEqualTo(ESCOLA);
        assertThat(registro.getTentativas()).isEqualTo(1);
        assertThat(registro.getProcessadoEm()).isEqualTo(AGORA);
        // O UPDATE nativo da cobranca desanexa o registro: o fim do evento precisa de save explicito.
        verify(eventoRepository).save(registro);
    }

    @Test
    void semIdExternoGravadoAchaACobrancaPelaReferencia() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno(COBRANCA_EXTERNA)).thenReturn(List.of());
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA)).thenReturn(linhaDaCobranca("EM_ATRASO"));
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.marcarPagaPeloProvedor(COBRANCA, AGORA)).thenReturn(1);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        verify(pagamentoRepository).save(any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    @Test
    void avisoDeContaDeOutraEscolaEIgnorado() {
        cobrancaEncontradaPeloIdExterno("PENDENTE");
        when(contaRepository.buscarEscolaPorIdContaExterna("conta-de-outra-escola"))
                .thenReturn(Optional.of(OUTRA_ESCOLA));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido("conta-de-outra-escola"));

        verify(pagamentoRepository, never()).save(any());
        verify(cobrancaRepository, never()).marcarPagaPeloProvedor(any(), any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void avisoDeContaDesconhecidaEIgnorado() {
        cobrancaEncontradaPeloIdExterno("PENDENTE");
        when(contaRepository.buscarEscolaPorIdContaExterna(anyString())).thenReturn(Optional.empty());
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido("conta-pai"));

        verify(pagamentoRepository, never()).save(any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void cobrancaQueNaoEDoEdukaEIgnorada() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno(COBRANCA_EXTERNA)).thenReturn(List.of());
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA)).thenReturn(List.of());
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        verify(pagamentoRepository, never()).save(any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void pagamentoJaRegistradoNaoDuplica() {
        cobrancaEncontradaPeloIdExterno("PAGA");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(pagamentoRepository.findByCobrancaId(COBRANCA)).thenReturn(Optional.of(pagamentoRegistrado(
                new BigDecimal("149.01"), LocalDate.of(2026, 10, 19))));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        verify(pagamentoRepository, never()).save(any());
        verify(cobrancaRepository, never()).marcarPagaPeloProvedor(any(), any());
        verifyNoInteractions(avisoCobrancaService, auditoriaService);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void avisoSeguinteDoMesmoPagamentoCompletaOCredito() {
        cobrancaEncontradaPeloIdExterno("PAGA");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        PagamentoCobranca confirmado = pagamentoRegistrado(null, null);
        when(pagamentoRepository.findByCobrancaId(COBRANCA)).thenReturn(Optional.of(confirmado));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        assertThat(confirmado.getValorLiquido()).isEqualByComparingTo("149.01");
        assertThat(confirmado.getDataCredito()).isEqualTo(LocalDate.of(2026, 10, 19));
        verify(pagamentoRepository, never()).save(any());
        verifyNoInteractions(avisoCobrancaService);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    @Test
    void pagamentoDeCobrancaAindaEmitindoTambemAMarcaComoEmitida() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno(COBRANCA_EXTERNA))
                .thenReturn(linhaDaCobranca("PENDENTE", "EMITINDO"));
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.marcarPagaPeloProvedor(COBRANCA, AGORA)).thenReturn(1);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        verify(cobrancaRepository).marcarEmitidaPeloProvedor(COBRANCA, COBRANCA_EXTERNA, AGORA);
        verify(pagamentoRepository).save(any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    @Test
    void pagamentoDeCobrancaCanceladaAMarcaComoPaga() {
        cobrancaEncontradaPeloIdExterno("CANCELADA");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.marcarPagaPeloProvedor(COBRANCA, AGORA)).thenReturn(0);
        when(cobrancaRepository.marcarPagaAposCancelamento(COBRANCA, AGORA)).thenReturn(1);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        verify(pagamentoRepository).save(any());
        verifyNoInteractions(avisoCobrancaService);
        // Sinalizado na auditoria, sem autor, e nao como um pagamento comum.
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.PAGO_APOS_CANCELAR,
                EntidadeAuditada.COBRANCA, COBRANCA, ESCOLA);
        verify(auditoriaService, never()).registrarDoProvedor(eq(OperacaoAuditoria.CONFIRMAR_PAGAMENTO), any(),
                any(), any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(registro.getMotivo()).contains("cancelamento");
    }

    // ---- Aplicar criacao e remocao (spec §8.4) ----

    @Test
    void criacaoConfirmadaTiraACobrancaDeEmitindo() {
        cobrancaEncontradaPelaReferencia("PENDENTE", "EMITINDO");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.marcarEmitidaPeloProvedor(COBRANCA, COBRANCA_EXTERNA, AGORA)).thenReturn(1);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.COBRANCA_CRIADA, CONTA_ESCOLA));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(registro.getEscolaId()).isEqualTo(ESCOLA);
    }

    @Test
    void criacaoDeCobrancaJaEmitidaNaoTemEfeito() {
        cobrancaEncontradaPeloIdExterno("PENDENTE");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.COBRANCA_CRIADA, CONTA_ESCOLA));

        verify(cobrancaRepository, never()).marcarEmitidaPeloProvedor(any(), any(), any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void remocaoDeCobrancaAindaEmitindoViraFalhaDeEmissao() {
        cobrancaEncontradaPelaReferencia("PENDENTE", "EMITINDO");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.marcarFalhaEmissaoPeloProvedor(eq(COBRANCA), anyString(), eq(AGORA))).thenReturn(1);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.COBRANCA_REMOVIDA, CONTA_ESCOLA));

        verify(cobrancaRepository, never()).cancelarPeloProvedor(any(), any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    @Test
    void remocaoDeCobrancaEmitidaEmAbertoACancela() {
        cobrancaEncontradaPeloIdExterno("EM_ATRASO");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.cancelarPeloProvedor(COBRANCA, AGORA)).thenReturn(1);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.COBRANCA_REMOVIDA, CONTA_ESCOLA));

        verify(cobrancaRepository, never()).marcarFalhaEmissaoPeloProvedor(any(), any(), any());
        // Ninguem do Eduka cancelou: CANCELAR sem autor.
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CANCELAR, EntidadeAuditada.COBRANCA,
                COBRANCA, ESCOLA);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    @Test
    void remocaoDeCobrancaPagaNaoTemEfeito() {
        cobrancaEncontradaPeloIdExterno("PAGA");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.cancelarPeloProvedor(COBRANCA, AGORA)).thenReturn(0);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.COBRANCA_REMOVIDA, CONTA_ESCOLA));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
        assertThat(registro.getMotivo()).contains("PAGA");
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void remocaoAvisadaPorContaDeOutraEscolaEIgnorada() {
        cobrancaEncontradaPeloIdExterno("PENDENTE");
        when(contaRepository.buscarEscolaPorIdContaExterna("conta-de-outra-escola"))
                .thenReturn(Optional.of(OUTRA_ESCOLA));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.COBRANCA_REMOVIDA, "conta-de-outra-escola"));

        verify(cobrancaRepository, never()).cancelarPeloProvedor(any(), any());
        verify(cobrancaRepository, never()).marcarFalhaEmissaoPeloProvedor(any(), any(), any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void cobrancaJaPagaNaMaoRegistraOPagamentoSemAvisarDeNovo() {
        cobrancaEncontradaPeloIdExterno("PAGA");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(cobrancaRepository.marcarPagaPeloProvedor(COBRANCA, AGORA)).thenReturn(0);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        verify(pagamentoRepository).save(any());
        verifyNoInteractions(avisoCobrancaService);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(registro.getMotivo()).contains("PAGA");
    }

    @Test
    void eventoJaAplicadoNaoEAplicadoDeNovo() {
        registro.setStatus(StatusEventoProvedor.PROCESSADO);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        verifyNoInteractions(cobrancaRepository, pagamentoRepository, avisoCobrancaService);
        assertThat(registro.getTentativas()).isZero();
    }

    @Test
    void avisoSemEfeitoNoWebhookEIgnorado() {
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));
        EventoProvedor vencida = new EventoProvedor("evt_2&2", TipoEventoProvedor.NAO_TRATADO, "PAYMENT_OVERDUE",
                CONTA_ESCOLA, cobrancaProvedor(null, StatusCobrancaProvedor.VENCIDA), null,
                LocalDate.of(2026, 10, 19), "{}");

        roteador.aplicar(EVENTO, vencida);

        verifyNoInteractions(cobrancaRepository, pagamentoRepository, avisoCobrancaService);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    // ---- Estorno e contestacao (RF22.2) ----

    @Test
    void estornoNoProvedorFicaNoLogSemMudarACobranca() {
        cobrancaEncontradaPeloIdExterno("PAGA");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.ESTORNO, CONTA_ESCOLA));

        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.ESTORNAR, EntidadeAuditada.COBRANCA,
                COBRANCA, ESCOLA);
        verify(cobrancaRepository, never()).marcarPagaPeloProvedor(any(), any());
        verify(cobrancaRepository, never()).cancelarPeloProvedor(any(), any());
        verifyNoInteractions(pagamentoRepository, avisoCobrancaService);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(registro.getMotivo()).contains("ESTORNO").contains("PAGA");
    }

    @Test
    void contestacaoNoProvedorFicaNoLogSemMudarACobranca() {
        cobrancaEncontradaPeloIdExterno("PAGA");
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.CONTESTACAO, CONTA_ESCOLA));

        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CONTESTAR, EntidadeAuditada.COBRANCA,
                COBRANCA, ESCOLA);
        verifyNoInteractions(pagamentoRepository, avisoCobrancaService);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    @Test
    void estornoAvisadoPorContaDeOutraEscolaEIgnorado() {
        cobrancaEncontradaPeloIdExterno("PAGA");
        when(contaRepository.buscarEscolaPorIdContaExterna("conta-de-outra-escola"))
                .thenReturn(Optional.of(OUTRA_ESCOLA));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeCobranca(TipoEventoProvedor.ESTORNO, "conta-de-outra-escola"));

        verifyNoInteractions(auditoriaService);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void estornoDaFaturaNaContaPaiFicaNoLogSemMudarAFatura() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PAGA, SituacaoProvedor.EMITIDA);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeFatura(TipoEventoProvedor.ESTORNO, null, true));

        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.ESTORNAR, EntidadeAuditada.FATURA_PLATAFORMA,
                FATURA, ESCOLA);
        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.PAGA);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    // ---- Fatura da plataforma (EDK-A-, Fluxo A) ----

    @Test
    void pagamentoDaFaturaNaContaPaiAMarcaComoPaga() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA);
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_PAI)).thenReturn(Optional.empty());
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeFatura(TipoEventoProvedor.PAGAMENTO_RECEBIDO, CONTA_PAI, true));

        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.PAGA);
        assertThat(fatura.getValorPago()).isEqualByComparingTo("150.00");
        assertThat(fatura.getDataPagamento()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(fatura.getIdTransacaoExterna()).isEqualTo("tx-pix-1");
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(registro.getEscolaId()).isEqualTo(ESCOLA);
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO,
                EntidadeAuditada.FATURA_PLATAFORMA, FATURA, ESCOLA);
        verifyNoInteractions(cobrancaRepository, pagamentoRepository, avisoCobrancaService);
    }

    @Test
    void avisoDeFaturaVindoDaContaDeUmaEscolaEIgnorado() {
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeFatura(TipoEventoProvedor.PAGAMENTO_RECEBIDO, CONTA_ESCOLA, true));

        verify(faturaRepository, never()).travar(any());
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void pagamentoDeFaturaCanceladaAMarcaComoPaga() {
        FaturaPlataforma fatura = fatura(StatusCobranca.CANCELADA, SituacaoProvedor.EMITIDA);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeFatura(TipoEventoProvedor.PAGAMENTO_CONFIRMADO, null, true));

        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.PAGA);
        assertThat(registro.getMotivo()).contains("cancelamento");
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.PAGO_APOS_CANCELAR,
                EntidadeAuditada.FATURA_PLATAFORMA, FATURA, ESCOLA);
    }

    @Test
    void segundoAvisoDePagamentoDaFaturaNaoMudaNada() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PAGA, SituacaoProvedor.EMITIDA);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeFatura(TipoEventoProvedor.PAGAMENTO_RECEBIDO, null, true));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void criacaoConfirmadaTiraAFaturaDeEmitindo() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PENDENTE, SituacaoProvedor.EMITINDO);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeFatura(TipoEventoProvedor.COBRANCA_CRIADA, null, false));

        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITIDA);
        assertThat(fatura.getIdCobrancaExterna()).isEqualTo(COBRANCA_EXTERNA);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    @Test
    void remocaoCancelaAFaturaEmitidaEmAberto() {
        FaturaPlataforma fatura = fatura(StatusCobranca.EM_ATRASO, SituacaoProvedor.EMITIDA);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeFatura(TipoEventoProvedor.COBRANCA_REMOVIDA, null, false));

        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.CANCELADA);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CANCELAR, EntidadeAuditada.FATURA_PLATAFORMA,
                FATURA, ESCOLA);
    }

    @Test
    void faturaDesconhecidaEIgnorada() {
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.empty());
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, avisoDeFatura(TipoEventoProvedor.PAGAMENTO_RECEBIDO, null, true));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void referenciaDeFaturaSoValeComUuid() {
        assertThat(RoteadorEventos.idDaFatura("EDK-A-" + FATURA)).contains(FATURA);
        assertThat(RoteadorEventos.idDaFatura("EDK-A-x")).isEmpty();
        assertThat(RoteadorEventos.idDaFatura("EDK-B-" + FATURA)).isEmpty();
    }

    @Test
    void falhaDeixaOEventoEmErroComOMotivo() {
        when(eventoRepository.findById(EVENTO)).thenReturn(Optional.of(registro));

        roteador.marcarErro(EVENTO, new IllegalStateException("banco fora"));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.ERRO);
        assertThat(registro.getMotivo()).isEqualTo("IllegalStateException: banco fora");
        assertThat(registro.getTentativas()).isEqualTo(1);
    }

    @Test
    void referenciaSoValeNoFormatoDaCobrancaDeAluno() {
        assertThat(RoteadorEventos.idDaReferencia("EDK-B-" + COBRANCA)).contains(COBRANCA);
        assertThat(RoteadorEventos.idDaReferencia("EDK-B-nao-e-uuid")).isEmpty();
        assertThat(RoteadorEventos.idDaReferencia("eduka-spike-sub-cob-1")).isEmpty();
        assertThat(RoteadorEventos.idDaReferencia(null)).isEmpty();
    }

    // ---- Situacao da conta ----

    @Test
    void avisoDeContaGravaASituacaoDoPayloadNaoONomeDoAviso() {
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(contaRepository.gravarSituacao(CONTA_ESCOLA, "APROVADA", "APROVADO", "APROVADO", "APROVADO",
                "APROVADO", AGORA)).thenReturn(1);

        // A11: o aviso "aguardando aprovacao" chegou com a foto ja aprovada.
        roteador.aplicar(EVENTO, avisoDeConta(CONTA_ESCOLA, new SituacaoContaProvedor(SituacaoCadastral.APROVADO,
                SituacaoCadastral.APROVADO, SituacaoCadastral.APROVADO, SituacaoCadastral.APROVADO)));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
    }

    @Test
    void contaAprovadaPeloAvisoFicaNoLogSemAutor() {
        UUID conta = UUID.randomUUID();
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(contaRepository.buscarPorIdContaExterna(CONTA_ESCOLA))
                .thenReturn(Optional.of(contaNoBanco(conta, "AGUARDANDO_APROVACAO")));
        when(contaRepository.gravarSituacao(eq(CONTA_ESCOLA), eq("APROVADA"), any(), any(), any(), any(), eq(AGORA)))
                .thenReturn(1);

        roteador.aplicar(EVENTO, avisoDeConta(CONTA_ESCOLA, new SituacaoContaProvedor(SituacaoCadastral.APROVADO,
                null, null, null)));

        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.ALTERAR_SITUACAO,
                EntidadeAuditada.CONTA_RECEBIMENTO, conta, ESCOLA);
    }

    @Test
    void avisoQueRepeteASituacaoDaContaNaoGeraLog() {
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(contaRepository.buscarPorIdContaExterna(CONTA_ESCOLA))
                .thenReturn(Optional.of(contaNoBanco(UUID.randomUUID(), "APROVADA")));
        when(contaRepository.gravarSituacao(eq(CONTA_ESCOLA), eq("APROVADA"), any(), any(), any(), any(), eq(AGORA)))
                .thenReturn(1);

        roteador.aplicar(EVENTO, avisoDeConta(CONTA_ESCOLA, new SituacaoContaProvedor(SituacaoCadastral.APROVADO,
                null, null, null)));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.PROCESSADO);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void avisoDeContaQueNaoEDeEscolaEIgnorado() {
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_PAI)).thenReturn(Optional.empty());

        roteador.aplicar(EVENTO, avisoDeConta(CONTA_PAI, new SituacaoContaProvedor(SituacaoCadastral.APROVADO,
                null, null, null)));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
        verify(contaRepository, never()).gravarSituacao(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void avisoDeContaAindaEmCriacaoEIgnorado() {
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));
        when(contaRepository.gravarSituacao(eq(CONTA_ESCOLA), eq("AGUARDANDO_APROVACAO"), anyString(), any(), any(),
                any(), eq(AGORA))).thenReturn(0);

        roteador.aplicar(EVENTO, avisoDeConta(CONTA_ESCOLA, new SituacaoContaProvedor(SituacaoCadastral.EM_ANALISE,
                null, null, null)));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
    }

    @Test
    void avisoDeContaSemSituacaoFicaParaARotina() {
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));
        when(contaRepository.buscarEscolaPorIdContaExterna(CONTA_ESCOLA)).thenReturn(Optional.of(ESCOLA));

        roteador.aplicar(EVENTO, avisoDeConta(CONTA_ESCOLA, null));

        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
        verify(contaRepository, never()).gravarSituacao(any(), any(), any(), any(), any(), any(), any());
    }

    private static ContaPorIdExterno contaNoBanco(UUID id, String status) {
        return new ContaPorIdExterno() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public UUID getEscolaId() {
                return ESCOLA;
            }

            @Override
            public String getStatus() {
                return status;
            }
        };
    }

    private static EventoProvedor avisoDeConta(String idConta, SituacaoContaProvedor situacao) {
        return new EventoProvedor("evt_conta&1", TipoEventoProvedor.SITUACAO_CONTA_ALTERADA,
                "ACCOUNT_STATUS_GENERAL_APPROVAL_AWAITING_APPROVAL", idConta, null, situacao, null, "{}");
    }

    // ---- Pagamento achado ao cancelar (spec §8.5) ----

    @Test
    void pagamentoAchadoAoCancelarERegistradoSemEvento() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA)).thenReturn(linhaDaCobranca("PENDENTE"));
        when(cobrancaRepository.marcarPagaPeloProvedor(COBRANCA, AGORA)).thenReturn(1);

        roteador.registrarPagamentoEncontrado(COBRANCA,
                cobrancaProvedor(new BigDecimal("150.00"), StatusCobrancaProvedor.RECEBIDA));

        ArgumentCaptor<PagamentoCobranca> pagamento = ArgumentCaptor.forClass(PagamentoCobranca.class);
        verify(pagamentoRepository).save(pagamento.capture());
        assertThat(pagamento.getValue().getOrigem()).isEqualTo(OrigemPagamento.ONLINE);
        assertThat(pagamento.getValue().getValorPago()).isEqualByComparingTo("150.00");
        assertThat(pagamento.getValue().getEventoId()).isNull();
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO,
                EntidadeAuditada.COBRANCA, COBRANCA, ESCOLA);
        verify(avisoCobrancaService).publicar(eq(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO),
                any(CobrancaParaAviso.class));
    }

    @Test
    void pagamentoAchadoAoCancelarJaRegistradoPeloAvisoNaoDuplica() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA)).thenReturn(linhaDaCobranca("PAGA"));
        when(pagamentoRepository.findByCobrancaId(COBRANCA)).thenReturn(Optional.of(pagamentoRegistrado(
                new BigDecimal("149.01"), LocalDate.of(2026, 10, 19))));

        roteador.registrarPagamentoEncontrado(COBRANCA,
                cobrancaProvedor(new BigDecimal("150.00"), StatusCobrancaProvedor.RECEBIDA));

        verify(pagamentoRepository, never()).save(any());
        verify(cobrancaRepository, never()).marcarPagaPeloProvedor(any(), any());
        verifyNoInteractions(avisoCobrancaService, auditoriaService);
    }

    // ---- Reconciliacao (NOVA-4, spec §8.4) ----

    @Test
    void cobrancaAchadaNoProvedorSaiDeEmitindoComoNoAvisoDeCriacao() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA))
                .thenReturn(linhaDaCobranca("PENDENTE", "EMITINDO"));
        when(cobrancaRepository.marcarEmitidaPeloProvedor(COBRANCA, COBRANCA_EXTERNA, AGORA)).thenReturn(1);

        assertThat(roteador.reconciliarCobranca(COBRANCA,
                Optional.of(cobrancaProvedor(null, StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO)))).isTrue();

        verify(pagamentoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService, avisoCobrancaService);
    }

    @Test
    void cobrancaAchadaJaPagaFicaEmitidaEPagaComoNoAvisoDePagamento() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA))
                .thenReturn(linhaDaCobranca("PENDENTE", "EMITINDO"));
        when(cobrancaRepository.marcarEmitidaPeloProvedor(COBRANCA, COBRANCA_EXTERNA, AGORA)).thenReturn(1);
        when(cobrancaRepository.marcarPagaPeloProvedor(COBRANCA, AGORA)).thenReturn(1);

        assertThat(roteador.reconciliarCobranca(COBRANCA,
                Optional.of(cobrancaProvedor(new BigDecimal("150.00"), StatusCobrancaProvedor.RECEBIDA)))).isTrue();

        ArgumentCaptor<PagamentoCobranca> pagamento = ArgumentCaptor.forClass(PagamentoCobranca.class);
        verify(pagamentoRepository).save(pagamento.capture());
        assertThat(pagamento.getValue().getOrigem()).isEqualTo(OrigemPagamento.ONLINE);
        assertThat(pagamento.getValue().getEventoId()).isNull();
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO,
                EntidadeAuditada.COBRANCA, COBRANCA, ESCOLA);
        verify(avisoCobrancaService).publicar(eq(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO),
                any(CobrancaParaAviso.class));
    }

    @Test
    void cobrancaQueNaoExisteNoProvedorPodeSerEmitidaDeNovo() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA))
                .thenReturn(linhaDaCobranca("PENDENTE", "EMITINDO"));
        when(cobrancaRepository.marcarFalhaEmissaoPeloProvedor(COBRANCA, RoteadorEventos.NAO_ACHADA_NO_PROVEDOR,
                AGORA)).thenReturn(1);

        assertThat(roteador.reconciliarCobranca(COBRANCA, Optional.empty())).isTrue();

        verify(cobrancaRepository, never()).marcarEmitidaPeloProvedor(any(), any(), any());
    }

    @Test
    void cobrancaRemovidaNoProvedorContaComoNaoAchada() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA))
                .thenReturn(linhaDaCobranca("PENDENTE", "EMITINDO"));
        when(cobrancaRepository.marcarFalhaEmissaoPeloProvedor(COBRANCA, RoteadorEventos.NAO_ACHADA_NO_PROVEDOR,
                AGORA)).thenReturn(1);

        assertThat(roteador.reconciliarCobranca(COBRANCA,
                Optional.of(cobrancaProvedor(null, StatusCobrancaProvedor.REMOVIDA)))).isTrue();
    }

    @Test
    void cobrancaQueOAvisoJaTirouDeEmitindoNaoMuda() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA)).thenReturn(linhaDaCobranca("PENDENTE"));

        assertThat(roteador.reconciliarCobranca(COBRANCA, Optional.empty())).isFalse();

        verify(cobrancaRepository, never()).marcarFalhaEmissaoPeloProvedor(any(), any(), any());
        verify(cobrancaRepository, never()).marcarEmitidaPeloProvedor(any(), any(), any());
    }

    @Test
    void pagamentoQueOAvisoJaGravouNaoDuplicaNaReconciliacao() {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA))
                .thenReturn(linhaDaCobranca("PAGA", "EMITINDO"));
        when(cobrancaRepository.marcarEmitidaPeloProvedor(COBRANCA, COBRANCA_EXTERNA, AGORA)).thenReturn(1);
        when(pagamentoRepository.findByCobrancaId(COBRANCA)).thenReturn(Optional.of(pagamentoRegistrado(
                new BigDecimal("149.01"), LocalDate.of(2026, 10, 19))));

        assertThat(roteador.reconciliarCobranca(COBRANCA,
                Optional.of(cobrancaProvedor(new BigDecimal("150.00"), StatusCobrancaProvedor.RECEBIDA)))).isTrue();

        verify(pagamentoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService, avisoCobrancaService);
    }

    @Test
    void faturaAchadaNaContaPaiSaiDeEmitindo() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PENDENTE, SituacaoProvedor.EMITINDO);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));

        assertThat(roteador.reconciliarFatura(FATURA, Optional.of(avisoDeFatura(TipoEventoProvedor.COBRANCA_CRIADA,
                CONTA_PAI, false).cobranca()))).isTrue();

        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITIDA);
        assertThat(fatura.getIdCobrancaExterna()).isEqualTo(COBRANCA_EXTERNA);
        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.PENDENTE);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void pagamentoDaFaturaEmitidaAchadoSemAvisoAMarcaComoPaga() {
        FaturaPlataforma fatura = fatura(StatusCobranca.EM_ATRASO, SituacaoProvedor.EMITIDA);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));

        assertThat(roteador.registrarPagamentoDaFaturaEncontrado(FATURA,
                avisoDeFatura(TipoEventoProvedor.PAGAMENTO_RECEBIDO, CONTA_PAI, true).cobranca())).isTrue();

        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.PAGA);
        assertThat(fatura.getValorPago()).isEqualByComparingTo("150.00");
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO,
                EntidadeAuditada.FATURA_PLATAFORMA, FATURA, ESCOLA);
    }

    @Test
    void pagamentoDaFaturaQueOAvisoJaGravouNaoMudaNada() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PAGA, SituacaoProvedor.EMITIDA);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));

        assertThat(roteador.registrarPagamentoDaFaturaEncontrado(FATURA,
                avisoDeFatura(TipoEventoProvedor.PAGAMENTO_RECEBIDO, CONTA_PAI, true).cobranca())).isFalse();

        verifyNoInteractions(auditoriaService);
    }

    @Test
    void faturaAchadaJaPagaFicaPagaEAuditada() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PENDENTE, SituacaoProvedor.EMITINDO);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));

        assertThat(roteador.reconciliarFatura(FATURA, Optional.of(avisoDeFatura(
                TipoEventoProvedor.PAGAMENTO_RECEBIDO, CONTA_PAI, true).cobranca()))).isTrue();

        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITIDA);
        assertThat(fatura.getStatus()).isEqualTo(StatusCobranca.PAGA);
        assertThat(fatura.getValorPago()).isEqualByComparingTo("150.00");
        verify(auditoriaService).registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO,
                EntidadeAuditada.FATURA_PLATAFORMA, FATURA, ESCOLA);
    }

    @Test
    void faturaQueNaoExisteNaContaPaiPodeSerEmitidaDeNovo() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PENDENTE, SituacaoProvedor.EMITINDO);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));

        assertThat(roteador.reconciliarFatura(FATURA, Optional.empty())).isTrue();

        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.FALHA_EMISSAO);
        assertThat(fatura.getUltimoErroProvedor()).isEqualTo(RoteadorEventos.NAO_ACHADA_NO_PROVEDOR);
    }

    @Test
    void faturaQueOAvisoJaTirouDeEmitindoNaoMuda() {
        FaturaPlataforma fatura = fatura(StatusCobranca.PENDENTE, SituacaoProvedor.EMITIDA);
        when(faturaRepository.travar(FATURA)).thenReturn(Optional.of(fatura));

        assertThat(roteador.reconciliarFatura(FATURA, Optional.empty())).isFalse();

        assertThat(fatura.getSituacaoProvedor()).isEqualTo(SituacaoProvedor.EMITIDA);
    }

    // ---- Apoio ----

    private void cobrancaEncontradaPeloIdExterno(String status) {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno(COBRANCA_EXTERNA))
                .thenReturn(linhaDaCobranca(status));
    }

    /** Ainda EMITINDO: o id externo nao foi gravado, entao so a referencia acha a cobranca. */
    private void cobrancaEncontradaPelaReferencia(String status, String situacao) {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno(COBRANCA_EXTERNA)).thenReturn(List.of());
        when(cobrancaRepository.buscarParaPagamentoOnlinePorId(COBRANCA)).thenReturn(linhaDaCobranca(status, situacao));
    }

    private static List<Object[]> linhaDaCobranca(String status) {
        return linhaDaCobranca(status, "EMITIDA");
    }

    /** Colunas na ordem de {@code CobrancaRepository#buscarParaPagamentoOnline*}. */
    private static List<Object[]> linhaDaCobranca(String status, String situacao) {
        List<Object[]> linhas = new ArrayList<>();
        linhas.add(new Object[] { COBRANCA, ESCOLA, UUID.randomUUID(), null, "Mensalidade de outubro",
                new BigDecimal("150.00"), LocalDate.of(2026, 10, 24), status, "PIX", situacao });
        return linhas;
    }

    private static PagamentoCobranca pagamentoRegistrado(BigDecimal valorLiquido, LocalDate dataCredito) {
        return PagamentoCobranca.builder()
                .escolaId(ESCOLA)
                .cobrancaId(COBRANCA)
                .valorPago(new BigDecimal("150.00"))
                .valorLiquido(valorLiquido)
                .dataPagamento(LocalDate.of(2026, 10, 19))
                .dataCredito(dataCredito)
                .formaPagamento(FormaPagamento.PIX)
                .origem(OrigemPagamento.ONLINE)
                .idTransacaoExterna("tx-pix-1")
                .build();
    }

    private static FaturaPlataforma fatura(StatusCobranca status, SituacaoProvedor situacao) {
        return FaturaPlataforma.builder().id(FATURA).escolaId(ESCOLA).competencia(LocalDate.of(2026, 10, 1))
                .descricao("Eduka outubro").valor(new BigDecimal("150.00")).dataVencimento(LocalDate.of(2026, 10, 24))
                .status(status).situacaoProvedor(situacao)
                .idCobrancaExterna(situacao == SituacaoProvedor.EMITIDA ? COBRANCA_EXTERNA : null)
                .formaPagamento(FormaPagamento.PIX).criadaPor(UUID.randomUUID()).build();
    }

    private static EventoProvedor avisoDeFatura(TipoEventoProvedor tipo, String idConta, boolean paga) {
        CobrancaProvedor cobranca = new CobrancaProvedor(COBRANCA_EXTERNA, "EDK-A-" + FATURA,
                paga ? StatusCobrancaProvedor.RECEBIDA : StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO,
                FormaPagamento.PIX, new BigDecimal("150.00"), paga ? new BigDecimal("150.00") : null, null,
                paga ? LocalDate.of(2026, 10, 19) : null, null, paga ? "tx-pix-1" : null, null);
        return new EventoProvedor("evt_4&4", tipo, tipo.name(), idConta, cobranca, null, LocalDate.of(2026, 10, 19),
                "{}");
    }

    private static EventoProvedor avisoDeCobranca(TipoEventoProvedor tipo, String idConta) {
        return new EventoProvedor("evt_3&3", tipo, tipo.name(), idConta,
                cobrancaProvedor(null, StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO), null,
                LocalDate.of(2026, 10, 19), "{}");
    }

    private static EventoProvedor pagamentoRecebido(String idConta) {
        return new EventoProvedor("evt_1&1", TipoEventoProvedor.PAGAMENTO_RECEBIDO, "PAYMENT_RECEIVED", idConta,
                cobrancaProvedor(new BigDecimal("150.00"), StatusCobrancaProvedor.RECEBIDA), null,
                LocalDate.of(2026, 10, 19), "{}");
    }

    private static CobrancaProvedor cobrancaProvedor(BigDecimal valorPago, StatusCobrancaProvedor status) {
        boolean paga = valorPago != null;
        return new CobrancaProvedor(COBRANCA_EXTERNA, "EDK-B-" + COBRANCA, status, FormaPagamento.PIX,
                new BigDecimal("150.00"), valorPago, paga ? new BigDecimal("149.01") : null,
                paga ? LocalDate.of(2026, 10, 19) : null, paga ? LocalDate.of(2026, 10, 19) : null,
                paga ? "tx-pix-1" : COBRANCA_EXTERNA, null);
    }
}
