package br.pucminas.eduka.backend.service.acesso;

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

import br.pucminas.eduka.backend.domain.notificacao.TipoNotificacao;
import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.NotificacaoService;

@ExtendWith(MockitoExtension.class)
class CodigoLoginEmailListenerTest {

    private static final VerificacaoLoginProperties REGRAS = new VerificacaoLoginProperties(true, 5, 5, 60, 5, false);
    private static final Instant AGORA = Instant.parse("2026-10-19T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    private NotificacaoService notificacaoService;

    private CodigoLoginGerado gerado(UUID usuarioId, String nome) {
        return new CodigoLoginGerado(usuarioId, nome, "Ana@Escola.com", "048213");
    }

    @Test
    void enfileiraOCodigoValendoSoEnquantoOCodigoVale() {
        CodigoLoginEmailListener listener = new CodigoLoginEmailListener(notificacaoService, REGRAS, CLOCK,
                "https://eduka.com.br/");
        UUID usuarioId = UUID.randomUUID();

        listener.enviar(gerado(usuarioId, "Ana"));

        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        // Codigo de 5 minutos: depois disso a fila nao envia mais.
        verify(notificacaoService).enfileirar(eq(TipoNotificacao.CODIGO_LOGIN), eq(usuarioId), captor.capture(),
                eq(AGORA.plusSeconds(5 * 60)));
        Email email = captor.getValue();
        assertThat(email.destinatario()).isEqualTo("Ana@Escola.com");
        assertThat(email.nomeDestinatario()).isEqualTo("Ana");
        assertThat(email.assunto()).isEqualTo(CodigoLoginEmailListener.ASSUNTO);
        assertThat(email.texto()).contains("048213", "5 minutos");
        assertThat(email.html()).contains("048213", "5 minutos",
                "src=\"https://eduka.com.br/email/eduka-logo-branca.png\"");
    }

    @Test
    void nomeComHtmlEhEscapadoNoCorpoHtml() {
        CodigoLoginEmailListener listener = new CodigoLoginEmailListener(notificacaoService, REGRAS, CLOCK,
                "http://localhost:5173");

        Email email = listener.montarEmail(gerado(UUID.randomUUID(), "<script>x</script>"));

        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
