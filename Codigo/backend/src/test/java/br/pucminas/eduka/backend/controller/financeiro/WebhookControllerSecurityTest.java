package br.pucminas.eduka.backend.controller.financeiro;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.pagamento.WebhookNaoAutorizadoException;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;

/** {@code /webhooks/asaas}: publica para o JWT, protegida pelo token do webhook. */
@WebMvcTest(WebhookController.class)
@Import({ JwtConfig.class, JwtService.class, ApiExceptionHandler.class })
class WebhookControllerSecurityTest {

    private static final String CORPO = "{\"id\":\"evt_1&1\",\"event\":\"PAYMENT_RECEIVED\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentRoutingService paymentRoutingService;

    @Test
    void asaasChamaSemJwtEComOToken() throws Exception {
        mockMvc.perform(post("/webhooks/asaas")
                .header("Asaas-Access-Token", "token-do-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO))
                .andExpect(status().isOk());

        verify(paymentRoutingService).registrarEvento("token-do-webhook", CORPO);
    }

    @Test
    void semTokenOuComTokenErradoResponde401() throws Exception {
        doThrow(new WebhookNaoAutorizadoException()).when(paymentRoutingService)
                .registrarEvento(isNull(), any());

        mockMvc.perform(post("/webhooks/asaas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corpoIlegivelResponde400() throws Exception {
        doThrow(new IllegalArgumentException("Evento do provedor ilegivel")).when(paymentRoutingService)
                .registrarEvento(eq("token-do-webhook"), any());

        mockMvc.perform(post("/webhooks/asaas")
                .header("Asaas-Access-Token", "token-do-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("nao e json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soOPostEPublico() throws Exception {
        mockMvc.perform(get("/webhooks/asaas"))
                .andExpect(status().isUnauthorized());
    }
}
