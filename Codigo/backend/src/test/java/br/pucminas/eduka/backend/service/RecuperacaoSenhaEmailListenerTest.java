package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.domain.TipoNotificacao;
import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.NotificacaoService;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaEmailListenerTest {

    private static final RecuperacaoSenhaProperties REGRAS = new RecuperacaoSenhaProperties(15, 5, 60, 5, 60, false);
    private static final Instant AGORA = Instant.parse("2026-11-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    private NotificacaoService notificacaoService;

    private CodigoRecuperacaoSenhaGerado gerado(UUID usuarioId, String nome) {
        return new CodigoRecuperacaoSenhaGerado(usuarioId, nome, "ana@escola.com", "407215");
    }

    @Test
    void enfileiraOCodigoValendoSoEnquantoOCodigoVale() {
        RecuperacaoSenhaEmailListener listener = new RecuperacaoSenhaEmailListener(notificacaoService, REGRAS,
                CLOCK, "https://eduka.com.br/");
        UUID usuarioId = UUID.randomUUID();

        listener.enviar(gerado(usuarioId, "Ana"));

        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(notificacaoService).enfileirar(eq(TipoNotificacao.RECUPERACAO_SENHA), eq(usuarioId),
                captor.capture(), eq(AGORA.plusSeconds(15 * 60)));
        Email email = captor.getValue();
        assertThat(email.destinatario()).isEqualTo("ana@escola.com");
        assertThat(email.assunto()).isEqualTo(RecuperacaoSenhaEmailListener.ASSUNTO);
        assertThat(email.texto()).contains("407215", "15 minutos");
        assertThat(email.html()).contains("407215", "15 minutos",
                "src=\"https://eduka.com.br/email/eduka-logo-branca.png\"");
    }

    @Test
    void nomeComHtmlEhEscapadoNoCorpoHtml() {
        RecuperacaoSenhaEmailListener listener = new RecuperacaoSenhaEmailListener(notificacaoService, REGRAS,
                CLOCK, "http://localhost:5173");

        Email email = listener.montarEmail(gerado(UUID.randomUUID(), "<script>x</script>"));

        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
