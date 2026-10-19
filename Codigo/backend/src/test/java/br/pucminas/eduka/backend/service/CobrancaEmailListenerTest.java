package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.domain.TipoNotificacao;
import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.NotificacaoService;

class CobrancaEmailListenerTest {

    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final CobrancaEmailListener listener = new CobrancaEmailListener(notificacaoService,
            "https://app.eduka.com.br/");

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
    void enfileiraComOTipoEACobrancaDeReferencia() {
        AvisoCobranca aviso = aviso(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO, false, Instant.now());

        listener.enviar(aviso);

        verify(notificacaoService).enfileirar(eq(TipoNotificacao.PAGAMENTO_CONFIRMADO), eq(aviso.cobrancaId()),
                any(Email.class), isNull());
    }

    @Test
    void lembreteDeVencimentoValeAteOFimDoDiaDoVencimentoNoFusoDaEscola() {
        AvisoCobranca aviso = aviso(AvisoCobranca.Tipo.VENCIMENTO_PROXIMO, true, null);

        listener.enviar(aviso);

        // Vence em 10/11/2026: vale ate 00:00 de 11/11 em Sao Paulo (03:00 UTC).
        verify(notificacaoService).enfileirar(eq(TipoNotificacao.VENCIMENTO_PROXIMO), eq(aviso.cobrancaId()),
                any(Email.class), eq(Instant.parse("2026-11-11T03:00:00Z")));
    }
}
