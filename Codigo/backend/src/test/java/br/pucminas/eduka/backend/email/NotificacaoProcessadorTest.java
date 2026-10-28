package br.pucminas.eduka.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import br.pucminas.eduka.backend.domain.Notificacao;
import br.pucminas.eduka.backend.domain.StatusNotificacao;
import br.pucminas.eduka.backend.domain.TipoNotificacao;
import br.pucminas.eduka.backend.repository.NotificacaoRepository;

@ExtendWith(MockitoExtension.class)
class NotificacaoProcessadorTest {

    private static final Instant AGORA = Instant.parse("2026-10-19T12:00:00Z");
    private static final NotificacaoProperties REGRAS = new NotificacaoProperties(3, Duration.ofMinutes(1),
            Duration.ofHours(2), 50);

    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private EmailSender emailSender;

    private NotificacaoProcessador processador;

    @BeforeEach
    void setUp() {
        processador = new NotificacaoProcessador(notificacaoRepository, emailSender, REGRAS,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private Notificacao naFila(int tentativas, Instant validaAte) {
        Notificacao notificacao = Notificacao.builder().id(UUID.randomUUID()).tipo(TipoNotificacao.CODIGO_LOGIN)
                .destinatario("ana@escola.com").nomeDestinatario("Ana").assunto("Seu codigo").html("<p>048213</p>")
                .texto("048213").status(StatusNotificacao.PENDENTE).tentativas(tentativas)
                .proximaTentativaEm(AGORA).validaAte(validaAte).criadoEm(AGORA).build();
        when(notificacaoRepository.travarParaEnvio(notificacao.getId())).thenReturn(Optional.of(notificacao));
        return notificacao;
    }

    @Test
    void enviaEDescartaOCorpo() {
        Notificacao notificacao = naFila(0, null);

        processador.processar(notificacao.getId());

        ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
        verify(emailSender).enviar(email.capture());
        assertThat(email.getValue().destinatario()).isEqualTo("ana@escola.com");
        assertThat(email.getValue().html()).isEqualTo("<p>048213</p>");
        assertThat(notificacao.getStatus()).isEqualTo(StatusNotificacao.ENVIADA);
        assertThat(notificacao.getTentativas()).isEqualTo(1);
        assertThat(notificacao.getEnviadoEm()).isEqualTo(AGORA);
        assertThat(notificacao.getHtml()).isNull();
        assertThat(notificacao.getTexto()).isNull();
    }

    @Test
    void falhaTemporariaAgendaNovaTentativaComEsperaCrescente() {
        Notificacao notificacao = naFila(1, null);
        doThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "ana@escola.com indisponivel"))
                .when(emailSender).enviar(any());

        processador.processar(notificacao.getId());

        assertThat(notificacao.getStatus()).isEqualTo(StatusNotificacao.PENDENTE);
        assertThat(notificacao.getTentativas()).isEqualTo(2);
        // Segunda falha: 1 min x 4.
        assertThat(notificacao.getProximaTentativaEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(4)));
        // Erro sem a mensagem do provedor, que ecoa o destinatario.
        assertThat(notificacao.getUltimoErro()).isEqualTo("HttpServerErrorException (HTTP 503)");
        assertThat(notificacao.getHtml()).isNotNull();
    }

    @Test
    void esgotadasAsTentativasFalhaDeVez() {
        Notificacao notificacao = naFila(2, null);
        doThrow(new IllegalStateException("timeout")).when(emailSender).enviar(any());

        processador.processar(notificacao.getId());

        assertThat(notificacao.getStatus()).isEqualTo(StatusNotificacao.FALHOU);
        assertThat(notificacao.getTentativas()).isEqualTo(3);
        assertThat(notificacao.getUltimoErro()).isEqualTo("IllegalStateException");
        assertThat(notificacao.getHtml()).isNull();
    }

    @Test
    void recusaDoProvedorFalhaSemNovaTentativa() {
        Notificacao notificacao = naFila(0, null);
        doThrow(new EmailRecusadoException(400)).when(emailSender).enviar(any());

        processador.processar(notificacao.getId());

        assertThat(notificacao.getStatus()).isEqualTo(StatusNotificacao.FALHOU);
        assertThat(notificacao.getTentativas()).isEqualTo(1);
        assertThat(notificacao.getUltimoErro()).isEqualTo("EmailRecusadoException (HTTP 400)");
    }

    @Test
    void vencidaExpiraSemEnviar() {
        Notificacao notificacao = naFila(1, AGORA.minusSeconds(1));

        processador.processar(notificacao.getId());

        verifyNoInteractions(emailSender);
        assertThat(notificacao.getStatus()).isEqualTo(StatusNotificacao.EXPIRADA);
        assertThat(notificacao.getTentativas()).isEqualTo(1);
        assertThat(notificacao.getTexto()).isNull();
    }

    @Test
    void travadaPorOutraExecucaoNaoFazNada() {
        UUID id = UUID.randomUUID();
        when(notificacaoRepository.travarParaEnvio(id)).thenReturn(Optional.empty());

        processador.processar(id);

        verifyNoInteractions(emailSender);
    }

    @Test
    void jaFinalizadaOuForaDaHoraNaoEnviaDeNovo() {
        Notificacao enviada = naFila(1, null);
        enviada.finalizar(StatusNotificacao.ENVIADA, AGORA);
        Notificacao agendada = naFila(1, null);
        agendada.setProximaTentativaEm(AGORA.plusSeconds(60));

        processador.processar(enviada.getId());
        processador.processar(agendada.getId());

        verifyNoInteractions(emailSender);
        assertThat(agendada.getTentativas()).isEqualTo(1);
    }

    @Test
    void esperaCresceAteOTeto() {
        assertThat(REGRAS.esperaApos(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(REGRAS.esperaApos(2)).isEqualTo(Duration.ofMinutes(4));
        assertThat(REGRAS.esperaApos(3)).isEqualTo(Duration.ofMinutes(16));
        assertThat(REGRAS.esperaApos(4)).isEqualTo(Duration.ofMinutes(64));
        assertThat(REGRAS.esperaApos(5)).isEqualTo(Duration.ofHours(2));
        assertThat(REGRAS.esperaApos(30)).isEqualTo(Duration.ofHours(2));
    }
}
