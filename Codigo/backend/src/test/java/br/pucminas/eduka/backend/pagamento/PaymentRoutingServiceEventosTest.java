package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;

/** Webhook no roteador: token, aviso repetido e falha ao aplicar. */
class PaymentRoutingServiceEventosTest {

    private static final String TOKEN = "token-do-webhook";
    private static final String CORPO = "{\"id\":\"evt_1&1\"}";
    private static final UUID EVENTO = UUID.randomUUID();
    private static final EventoProvedor RECEBIDO = new EventoProvedor("evt_1&1",
            TipoEventoProvedor.PAGAMENTO_RECEBIDO, "PAYMENT_RECEIVED", "conta", null, null,
            LocalDate.of(2026, 10, 19), "{}");

    private PaymentProvider provider;
    private RoteadorEventos eventos;
    private PaymentRoutingService roteador;

    @BeforeEach
    void setUp() {
        provider = mock(PaymentProvider.class);
        eventos = mock(RoteadorEventos.class);
        roteador = new PaymentRoutingService(new ChamadorProvedor(provider), mock(RoteadorConta.class),
                mock(RoteadorPagamento.class), eventos, mock(RoteadorFatura.class), mock(RoteadorSandbox.class));
    }

    @Test
    void tokenInvalidoNaoGravaNada() {
        when(provider.webhookAutentico("outro")).thenReturn(false);

        assertThatThrownBy(() -> roteador.registrarEvento("outro", CORPO))
                .isInstanceOf(WebhookNaoAutorizadoException.class);
        verify(provider, never()).interpretarEvento(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void avisoNovoEGravadoEAplicado() {
        when(provider.webhookAutentico(TOKEN)).thenReturn(true);
        when(provider.interpretarEvento(CORPO)).thenReturn(RECEBIDO);
        when(eventos.gravar(RECEBIDO)).thenReturn(Optional.of(EVENTO));

        roteador.registrarEvento(TOKEN, CORPO);

        verify(eventos).aplicar(EVENTO, RECEBIDO);
    }

    @Test
    void avisoRepetidoNaoEAplicado() {
        when(provider.webhookAutentico(TOKEN)).thenReturn(true);
        when(provider.interpretarEvento(CORPO)).thenReturn(RECEBIDO);
        when(eventos.gravar(RECEBIDO)).thenReturn(Optional.empty());

        roteador.registrarEvento(TOKEN, CORPO);

        verify(eventos, never()).aplicar(any(), any());
    }

    @Test
    void avisoRepetidoEmParaleloNaoEAplicado() {
        when(provider.webhookAutentico(TOKEN)).thenReturn(true);
        when(provider.interpretarEvento(CORPO)).thenReturn(RECEBIDO);
        when(eventos.gravar(RECEBIDO)).thenThrow(new DataIntegrityViolationException("uk"));
        when(eventos.jaRecebido("evt_1&1")).thenReturn(true);

        roteador.registrarEvento(TOKEN, CORPO);

        verify(eventos, never()).aplicar(any(), any());
    }

    @Test
    void outraViolacaoAoGravarNaoEEngolida() {
        when(provider.webhookAutentico(TOKEN)).thenReturn(true);
        when(provider.interpretarEvento(CORPO)).thenReturn(RECEBIDO);
        when(eventos.gravar(RECEBIDO)).thenThrow(new DataIntegrityViolationException("outra"));
        when(eventos.jaRecebido("evt_1&1")).thenReturn(false);

        assertThatThrownBy(() -> roteador.registrarEvento(TOKEN, CORPO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void avisoSemIdERecusado() {
        when(provider.webhookAutentico(TOKEN)).thenReturn(true);
        when(provider.interpretarEvento(CORPO)).thenReturn(new EventoProvedor(null,
                TipoEventoProvedor.NAO_TRATADO, "X", null, null, null, null, "{}"));

        assertThatThrownBy(() -> roteador.registrarEvento(TOKEN, CORPO))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventos);
    }

    @Test
    void avisoSemTipoERecusado() {
        when(provider.webhookAutentico(TOKEN)).thenReturn(true);
        when(provider.interpretarEvento(CORPO)).thenReturn(new EventoProvedor("evt_1&1",
                TipoEventoProvedor.NAO_TRATADO, null, null, null, null, null, "{}"));

        assertThatThrownBy(() -> roteador.registrarEvento(TOKEN, CORPO))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventos);
    }

    @Test
    void falhaAoAplicarDeixaEmErroSemFalharOWebhook() {
        when(provider.webhookAutentico(TOKEN)).thenReturn(true);
        when(provider.interpretarEvento(CORPO)).thenReturn(RECEBIDO);
        when(eventos.gravar(RECEBIDO)).thenReturn(Optional.of(EVENTO));
        IllegalStateException erro = new IllegalStateException("falhou");
        doThrow(erro).when(eventos).aplicar(EVENTO, RECEBIDO);

        assertThatCode(() -> roteador.registrarEvento(TOKEN, CORPO)).doesNotThrowAnyException();
        verify(eventos).marcarErro(EVENTO, erro);
    }

    @Test
    void processarEventoReinterpretaOPayloadGravado() {
        when(eventos.payloadPendente(EVENTO)).thenReturn(Optional.of("{\"saneado\":true}"));
        when(provider.interpretarEvento("{\"saneado\":true}")).thenReturn(RECEBIDO);

        roteador.processarEvento(EVENTO);

        verify(eventos).aplicar(EVENTO, RECEBIDO);
    }

    @Test
    void processarEventoJaTerminadoNaoFazNada() {
        when(eventos.payloadPendente(EVENTO)).thenReturn(Optional.empty());

        roteador.processarEvento(EVENTO);

        verify(eventos, never()).aplicar(any(), any());
        verify(provider, never()).interpretarEvento(any());
    }

    @Test
    void processarEventoNuncaLancaExcecao() {
        when(eventos.payloadPendente(EVENTO)).thenReturn(Optional.of("{}"));
        when(provider.interpretarEvento("{}")).thenThrow(new IllegalArgumentException("ilegivel"));
        doThrow(new IllegalStateException("banco fora")).when(eventos).marcarErro(eq(EVENTO), any());

        assertThatCode(() -> roteador.processarEvento(EVENTO)).doesNotThrowAnyException();
    }

    // ---- Agendador (NOVA-4) ----

    @Test
    void agendadorReaplicaCadaPendenteMesmoComUmFalhando() {
        UUID falha = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        when(eventos.pendentes(10, 50)).thenReturn(List.of(falha, ok));
        when(eventos.payloadPendente(falha)).thenReturn(Optional.of("{\"falha\":true}"));
        when(eventos.payloadPendente(ok)).thenReturn(Optional.of("{\"ok\":true}"));
        when(provider.interpretarEvento("{\"falha\":true}")).thenThrow(new IllegalArgumentException("ilegivel"));
        when(provider.interpretarEvento("{\"ok\":true}")).thenReturn(RECEBIDO);

        int reaplicados = roteador.processarEventosPendentes(10, 50);

        assertThat(reaplicados).isEqualTo(2);
        verify(eventos).marcarErro(eq(falha), any(IllegalArgumentException.class));
        verify(eventos).aplicar(ok, RECEBIDO);
    }

    @Test
    void semPendentesNaoFazNada() {
        when(eventos.pendentes(10, 50)).thenReturn(List.of());

        assertThat(roteador.processarEventosPendentes(10, 50)).isZero();
        verify(eventos, never()).payloadPendente(any());
        verifyNoInteractions(provider);
    }
}
