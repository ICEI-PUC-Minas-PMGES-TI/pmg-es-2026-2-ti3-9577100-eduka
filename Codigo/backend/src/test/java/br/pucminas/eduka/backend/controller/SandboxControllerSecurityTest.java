package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.exception.SandboxIndisponivelException;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * Atalhos de Sandbox (NOVA-6, spec §10.1 S1 e S2): MASTER aprova a conta,
 * FINANCEIRO confirma o pagamento e o ALUNO paga a propria cobranca; fora do
 * perfil dev as rotas nao existem (S04).
 */
class SandboxControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final String APROVAR = "/config-pagamento/conta/sandbox/aprovar";
    private static final String CONFIRMAR = "/cobrancas/" + UUID.randomUUID() + "/sandbox/confirmar-pagamento";
    private static final String PAGAR_ALUNO = "/portal/aluno/cobrancas/" + UUID.randomUUID() + "/sandbox/pagar";
    private static final String PAGAR_FATURA = "/config-pagamento/faturas/" + UUID.randomUUID() + "/sandbox/pagar";

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder pedido, String token) {
        return pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(JwtService jwtService, String perfil) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA);
    }

    @Nested
    @WebMvcTest(SandboxController.class)
    @ActiveProfiles("dev")
    @Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
    class NoPerfilDev {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JwtService jwtService;

        @MockitoBean
        private PaymentRoutingService paymentRoutingService;

        @Test
        void masterAprovaAConta() throws Exception {
            mockMvc.perform(comToken(post(APROVAR), token(jwtService, "MASTER"))).andExpect(status().isAccepted());
            verify(paymentRoutingService).sandboxAprovarConta();
        }

        @Test
        void financeiroConfirmaOPagamento() throws Exception {
            mockMvc.perform(comToken(post(CONFIRMAR), token(jwtService, "FINANCEIRO")))
                    .andExpect(status().isAccepted());
            verify(paymentRoutingService).sandboxConfirmarPagamento(any());
        }

        @Test
        void outrosPerfisRecebem403() throws Exception {
            for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
                mockMvc.perform(comToken(post(APROVAR), token(jwtService, perfil))).andExpect(status().isForbidden());
            }
            for (String perfil : List.of("SECRETARIA", "PROFESSOR", "ALUNO")) {
                mockMvc.perform(comToken(post(CONFIRMAR), token(jwtService, perfil)))
                        .andExpect(status().isForbidden());
            }
            verify(paymentRoutingService, never()).sandboxAprovarConta();
            verify(paymentRoutingService, never()).sandboxConfirmarPagamento(any());
        }

        @Test
        void alunoPagaAPropriaCobranca() throws Exception {
            mockMvc.perform(comToken(post(PAGAR_ALUNO), token(jwtService, "ALUNO")))
                    .andExpect(status().isAccepted());
            verify(paymentRoutingService).sandboxPagarComoAluno(any());
        }

        @Test
        void soOAlunoPagaPeloPortal() throws Exception {
            for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR")) {
                mockMvc.perform(comToken(post(PAGAR_ALUNO), token(jwtService, perfil)))
                        .andExpect(status().isForbidden());
            }
            verify(paymentRoutingService, never()).sandboxPagarComoAluno(any());
        }

        @Test
        void masterPagaAFaturaDoEduka() throws Exception {
            mockMvc.perform(comToken(post(PAGAR_FATURA), token(jwtService, "MASTER")))
                    .andExpect(status().isAccepted());
            verify(paymentRoutingService).sandboxPagarFatura(any());
        }

        @Test
        void soOMasterPagaAFatura() throws Exception {
            for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
                mockMvc.perform(comToken(post(PAGAR_FATURA), token(jwtService, perfil)))
                        .andExpect(status().isForbidden());
            }
            verify(paymentRoutingService, never()).sandboxPagarFatura(any());
        }

        @Test
        void foraDoSandboxE409ComCodigo() throws Exception {
            doThrow(new SandboxIndisponivelException()).when(paymentRoutingService).sandboxAprovarConta();

            mockMvc.perform(comToken(post(APROVAR), token(jwtService, "MASTER")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("SANDBOX_INDISPONIVEL"));
        }
    }

    /** Ambiente de apresentacao (prod,demo): os atalhos existem para mostrar o pagamento no Sandbox. */
    @Nested
    @WebMvcTest(SandboxController.class)
    @ActiveProfiles({ "prod", "demo" })
    @Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
    class NoPerfilDemo {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JwtService jwtService;

        @MockitoBean
        private PaymentRoutingService paymentRoutingService;

        @Test
        void masterAprovaAConta() throws Exception {
            mockMvc.perform(comToken(post(APROVAR), token(jwtService, "MASTER"))).andExpect(status().isAccepted());
            verify(paymentRoutingService).sandboxAprovarConta();
        }
    }

    @Nested
    @WebMvcTest(SandboxController.class)
    @Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
    class ForaDoPerfilDev {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JwtService jwtService;

        @MockitoBean
        private PaymentRoutingService paymentRoutingService;

        @Test
        void rotasNaoExistem() throws Exception {
            mockMvc.perform(comToken(post(APROVAR), token(jwtService, "MASTER"))).andExpect(status().isNotFound());
            mockMvc.perform(comToken(post(CONFIRMAR), token(jwtService, "FINANCEIRO")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(comToken(post(PAGAR_ALUNO), token(jwtService, "ALUNO")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(comToken(post(PAGAR_FATURA), token(jwtService, "MASTER")))
                    .andExpect(status().isNotFound());
            verify(paymentRoutingService, never()).sandboxAprovarConta();
            verify(paymentRoutingService, never()).sandboxPagarComoAluno(any());
            verify(paymentRoutingService, never()).sandboxPagarFatura(any());
        }
    }
}
