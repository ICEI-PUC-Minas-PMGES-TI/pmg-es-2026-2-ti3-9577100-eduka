package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
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
class AcessoInicialEmailListenerTest {

    private static final Instant EXPIRA_EM = Instant.parse("2026-10-05T15:00:00Z");

    @Mock
    private NotificacaoService notificacaoService;

    private AcessoInicialCriado acesso(UUID usuarioId, String nome) {
        return new AcessoInicialCriado(usuarioId, nome, "Ana@Escola.com", "ana@escola.com", EXPIRA_EM);
    }

    @Test
    void enfileiraParaOEmailDaPessoaValendoAteASenhaProvisoriaExpirar() {
        AcessoInicialEmailListener listener = new AcessoInicialEmailListener(notificacaoService,
                "https://eduka.com.br/");
        UUID usuarioId = UUID.randomUUID();

        listener.enviar(acesso(usuarioId, "Ana"));

        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(notificacaoService).enfileirar(eq(TipoNotificacao.ACESSO_INICIAL), eq(usuarioId), captor.capture(),
                eq(EXPIRA_EM));
        Email email = captor.getValue();
        assertThat(email.destinatario()).isEqualTo("Ana@Escola.com");
        assertThat(email.nomeDestinatario()).isEqualTo("Ana");
        assertThat(email.assunto()).isEqualTo(AcessoInicialEmailListener.ASSUNTO);
        assertThat(email.texto()).contains("Login: ana@escola.com", "seu CPF", "05/10/2026 às 12:00",
                "https://eduka.com.br/login");
        assertThat(email.html()).contains("ana@escola.com", "href=\"https://eduka.com.br/login\"",
                "05/10/2026 às 12:00");
    }

    @Test
    void logoApontaParaOFrontComTextoAlternativo() {
        AcessoInicialEmailListener listener = new AcessoInicialEmailListener(notificacaoService,
                "https://eduka.com.br/");

        Email email = listener.montarEmail(acesso(UUID.randomUUID(), "Ana"));

        assertThat(email.html()).contains("src=\"https://eduka.com.br/email/eduka-logo-branca.png\"",
                "alt=\"Eduka\"");
    }

    @Test
    void nomeComHtmlEhEscapadoNoCorpoHtml() {
        AcessoInicialEmailListener listener = new AcessoInicialEmailListener(notificacaoService,
                "http://localhost:5173");

        Email email = listener.montarEmail(acesso(UUID.randomUUID(), "<script>x</script>"));

        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
