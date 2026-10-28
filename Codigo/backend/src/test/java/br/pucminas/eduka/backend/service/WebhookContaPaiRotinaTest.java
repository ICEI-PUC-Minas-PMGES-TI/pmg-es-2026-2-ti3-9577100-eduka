package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.pagamento.ProvedorIndisponivelException;

/** Webhook da conta-pai na subida: so com a chave ligada, e falha do Asaas nao derruba a aplicacao. */
@ExtendWith(MockitoExtension.class)
class WebhookContaPaiRotinaTest {

    @Mock
    private PaymentRoutingService paymentRoutingService;

    @Test
    void desligadaNaoChamaOProvedor() {
        new WebhookContaPaiRotina(paymentRoutingService, false).garantirWebhook();

        verifyNoInteractions(paymentRoutingService);
    }

    @Test
    void ligadaGaranteOWebhook() {
        new WebhookContaPaiRotina(paymentRoutingService, true).garantirWebhook();

        verify(paymentRoutingService).garantirWebhookDaContaPai();
    }

    @Test
    void falhaDoProvedorSoFicaNoLog() {
        doThrow(new ProvedorIndisponivelException("Provedor de pagamento indisponivel (HTTP 503)"))
                .when(paymentRoutingService).garantirWebhookDaContaPai();

        assertThatCode(() -> new WebhookContaPaiRotina(paymentRoutingService, true).garantirWebhook())
                .doesNotThrowAnyException();
    }
}
