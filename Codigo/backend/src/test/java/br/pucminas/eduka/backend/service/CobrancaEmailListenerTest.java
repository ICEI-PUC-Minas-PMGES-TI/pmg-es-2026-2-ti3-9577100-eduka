package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.EmailSender;

class CobrancaEmailListenerTest {

    private final EmailSender emailSender = mock(EmailSender.class);
    private final CobrancaEmailListener listener = new CobrancaEmailListener(emailSender, "https://app.eduka.com.br/");

    private static AvisoCobranca aviso(AvisoCobranca.Tipo tipo, boolean paraAluno, Instant pagaEm) {
        return new AvisoCobranca(tipo, UUID.randomUUID(), "marta@exemplo.com", "Marta", paraAluno, "Ana",
                "Colégio <Horizonte>", "Mensalidade de novembro", new BigDecimal("1234.5"),
                LocalDate.of(2026, 11, 10), pagaEm);
    }

    @Test
    void cobrancaCriadaParaOResponsavelCitaOAluno() {
        Email email = listener.montarEmail(aviso(AvisoCobranca.Tipo.COBRANCA_CRIADA, false, null));

        assertThat(email.destinatario()).isEqualTo("marta@exemplo.com");
        assertThat(email.assunto()).isEqualTo("Nova cobrança: Mensalidade de novembro");
        assertThat(email.texto())
                .contains("Olá, Marta!", "lançou uma nova cobrança de Ana", "Vencimento: 10/11/2026",
                        "R$", "1.234,50", "https://app.eduka.com.br/login");
        assertThat(email.html())
                .contains("Colégio &lt;Horizonte&gt;")
                .doesNotContain("Colégio <Horizonte>")
                .contains("https://app.eduka.com.br/email/eduka-logo-branca.png");
    }

    @Test
    void vencimentoProximoParaOProprioAlunoNaoRepeteONome() {
        Email email = listener.montarEmail(aviso(AvisoCobranca.Tipo.VENCIMENTO_PROXIMO, true, null));

        assertThat(email.assunto()).isEqualTo("Lembrete: cobrança vence em 10/11/2026");
        assertThat(email.texto()).contains("a cobrança abaixo vence em 10/11/2026")
                .doesNotContain("cobrança de Ana")
                .contains("desconsidere");
    }

    @Test
    void pagamentoConfirmadoTrazAHoraDaBaixaNoFusoDaEscola() {
        // 13:30 UTC = 10:30 em Sao Paulo.
        Email email = listener.montarEmail(aviso(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO, false,
                Instant.parse("2026-11-08T13:30:00Z")));

        assertThat(email.assunto()).isEqualTo("Pagamento confirmado: Mensalidade de novembro");
        assertThat(email.texto()).contains("Pago em: 08/11/2026 às 10:30", "confirmou o pagamento da cobrança de Ana");
    }

    @Test
    void falhaNoEnvioNaoPropaga() {
        doThrow(new IllegalStateException("Brevo fora")).when(emailSender).enviar(any());

        assertThatCode(() -> listener.enviar(aviso(AvisoCobranca.Tipo.COBRANCA_CRIADA, false, null)))
                .doesNotThrowAnyException();
        verify(emailSender).enviar(any());
    }
}
