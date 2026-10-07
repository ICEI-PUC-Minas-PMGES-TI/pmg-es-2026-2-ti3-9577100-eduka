package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.EmailSender;

@ExtendWith(MockitoExtension.class)
class AcessoInicialEmailListenerTest {

    @Mock
    private EmailSender emailSender;

    private AcessoInicialCriado acesso(String nome) {
        return new AcessoInicialCriado(UUID.randomUUID(), nome, "Ana@Escola.com", "ana@escola.com",
                Instant.parse("2026-10-05T15:00:00Z"));
    }

    @Test
    void enviaParaOEmailDaPessoaComLoginValidadeELink() {
        AcessoInicialEmailListener listener = new AcessoInicialEmailListener(emailSender, "https://eduka.com.br/");

        listener.enviar(acesso("Ana"));

        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(emailSender).enviar(captor.capture());
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
        AcessoInicialEmailListener listener = new AcessoInicialEmailListener(emailSender, "https://eduka.com.br/");

        Email email = listener.montarEmail(acesso("Ana"));

        assertThat(email.html()).contains("src=\"https://eduka.com.br/email/eduka-logo-branca.png\"",
                "alt=\"Eduka\"");
    }

    @Test
    void nomeComHtmlEhEscapadoNoCorpoHtml() {
        AcessoInicialEmailListener listener = new AcessoInicialEmailListener(emailSender, "http://localhost:5173");

        Email email = listener.montarEmail(acesso("<script>x</script>"));

        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void falhaNoEnvioNaoPropaga() {
        // O cadastro ja foi commitado: erro do provedor de e-mail nao pode virar 500.
        doThrow(new IllegalStateException("Brevo fora")).when(emailSender).enviar(any());
        AcessoInicialEmailListener listener = new AcessoInicialEmailListener(emailSender, "http://localhost:5173");

        assertThatCode(() -> listener.enviar(acesso("Ana"))).doesNotThrowAnyException();
    }
}
