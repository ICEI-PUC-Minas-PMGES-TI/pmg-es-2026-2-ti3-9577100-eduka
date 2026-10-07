package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.EmailSender;

@ExtendWith(MockitoExtension.class)
class CodigoLoginEmailListenerTest {

    private static final VerificacaoLoginProperties REGRAS = new VerificacaoLoginProperties(true, 5, 5, 60, 5, false);

    @Mock
    private EmailSender emailSender;

    private CodigoLoginGerado gerado(String nome) {
        return new CodigoLoginGerado(UUID.randomUUID(), nome, "Ana@Escola.com", "048213");
    }

    @Test
    void enviaOCodigoEAValidadeParaOEmailDoUsuario() {
        CodigoLoginEmailListener listener = new CodigoLoginEmailListener(emailSender, REGRAS,
                "https://eduka.com.br/");

        listener.enviar(gerado("Ana"));

        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(emailSender).enviar(captor.capture());
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
        CodigoLoginEmailListener listener = new CodigoLoginEmailListener(emailSender, REGRAS,
                "http://localhost:5173");

        Email email = listener.montarEmail(gerado("<script>x</script>"));

        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void falhaNoEnvioNaoPropaga() {
        CodigoLoginEmailListener listener = new CodigoLoginEmailListener(emailSender, REGRAS,
                "http://localhost:5173");
        doThrow(new IllegalStateException("Brevo fora do ar")).when(emailSender).enviar(any());

        assertThatCode(() -> listener.enviar(gerado("Ana"))).doesNotThrowAnyException();
    }
}
