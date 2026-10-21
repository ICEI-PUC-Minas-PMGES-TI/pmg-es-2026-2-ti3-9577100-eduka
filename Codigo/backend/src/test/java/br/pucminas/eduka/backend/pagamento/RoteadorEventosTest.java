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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.StatusEventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.EventoProvedorRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
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

    @Mock
    private EventoProvedorRepository eventoRepository;
    @Mock
    private CobrancaRepository cobrancaRepository;
    @Mock
    private ContaRecebimentoEscolaRepository contaRepository;
    @Mock
    private PagamentoCobrancaRepository pagamentoRepository;
    @Mock
    private AvisoCobrancaService avisoCobrancaService;

    private RoteadorEventos roteador;
    private br.pucminas.eduka.backend.domain.EventoProvedor registro;

    @BeforeEach
    void setUp() {
        roteador = new RoteadorEventos(eventoRepository, cobrancaRepository, contaRepository, pagamentoRepository,
                avisoCobrancaService, Clock.fixed(AGORA, ZoneOffset.UTC));
        registro = br.pucminas.eduka.backend.domain.EventoProvedor.builder()
                .id(EVENTO)
                .idEventoExterno("evt_1&1")
                .tipoOriginal("PAYMENT_RECEIVED")
                .tipo(TipoEventoProvedor.PAGAMENTO_RECEBIDO)
                .payload("{}")
                .build();
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
        when(pagamentoRepository.existsByCobrancaId(COBRANCA)).thenReturn(true);
        when(eventoRepository.travar(EVENTO)).thenReturn(Optional.of(registro));

        roteador.aplicar(EVENTO, pagamentoRecebido(CONTA_ESCOLA));

        verify(pagamentoRepository, never()).save(any());
        verify(cobrancaRepository, never()).marcarPagaPeloProvedor(any(), any());
        verifyNoInteractions(avisoCobrancaService);
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
        EventoProvedor criada = new EventoProvedor("evt_2&2", TipoEventoProvedor.COBRANCA_CRIADA, "PAYMENT_CREATED",
                CONTA_ESCOLA, cobrancaProvedor(null, StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO), null,
                LocalDate.of(2026, 10, 19), "{}");

        roteador.aplicar(EVENTO, criada);

        verifyNoInteractions(cobrancaRepository, pagamentoRepository, avisoCobrancaService);
        assertThat(registro.getStatus()).isEqualTo(StatusEventoProvedor.IGNORADO);
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

    // ---- Apoio ----

    private void cobrancaEncontradaPeloIdExterno(String status) {
        when(cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno(COBRANCA_EXTERNA))
                .thenReturn(linhaDaCobranca(status));
    }

    /** Colunas na ordem de {@code CobrancaRepository#buscarParaPagamentoOnline*}. */
    private static List<Object[]> linhaDaCobranca(String status) {
        List<Object[]> linhas = new ArrayList<>();
        linhas.add(new Object[] { COBRANCA, ESCOLA, UUID.randomUUID(), null, "Mensalidade de outubro",
                new BigDecimal("150.00"), LocalDate.of(2026, 10, 24), status, "PIX" });
        return linhas;
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
