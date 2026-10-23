package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.dto.ContaRecebimentoResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.exception.ContaRecebimentoNaoAtivavelException;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.pagamento.ProvedorRecusouException;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * Contrato da conta de recebimento (spec §8.1): so o MASTER consulta, ativa e
 * sincroniza. As regras ficam no roteador, que aqui e mock.
 */
@WebMvcTest(ContaRecebimentoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class ContaRecebimentoControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final String CONTA = "/config-pagamento/conta";
    private static final String CADASTRO = "{\"razaoSocial\":\"Escola Horizonte Ltda\","
            + "\"emailFinanceiro\":\"financeiro@horizonte.edu.br\",\"telefoneCelular\":\"31999998888\","
            + "\"tipoEmpresa\":\"LIMITED\",\"faturamentoMensal\":50000.00,\"logradouro\":\"Rua das Flores\","
            + "\"numero\":\"100\",\"bairro\":\"Centro\",\"cep\":\"30110000\",\"dadosBancarios\":{"
            + "\"codigoBanco\":\"341\",\"agencia\":\"1234\",\"conta\":\"123456789\",\"digitoConta\":\"0\","
            + "\"tipo\":\"CORRENTE\"}}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PaymentRoutingService paymentRoutingService;

    private String token(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder pedido, String token) {
        return pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder ativar(String token, String corpo) {
        return comToken(post(CONTA + "/ativar"), token).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static ContaRecebimentoResponse resposta(String status) {
        return new ContaRecebimentoResponse(status, false, status, "Escola Horizonte Ltda",
                "financeiro@horizonte.edu.br", "31999998888", null, null, null, null, null, null, null,
                "12345678000195", "341", "6789", null, null, null, null, null, false, true);
    }

    @Test
    void soMasterUsaAContaDeRecebimento() throws Exception {
        for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(comToken(get(CONTA), token(perfil, ESCOLA))).andExpect(status().isForbidden());
            mockMvc.perform(ativar(token(perfil, ESCOLA), CADASTRO)).andExpect(status().isForbidden());
            mockMvc.perform(comToken(post(CONTA + "/sincronizar"), token(perfil, ESCOLA)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(ativar(token("SUPER_ADMIN", null), CADASTRO)).andExpect(status().isForbidden());
        verify(paymentRoutingService, never()).ativarRecebimentos(any());
    }

    @Test
    void ativarDevolveAContaSemChaveNemDadosBancarios() throws Exception {
        when(paymentRoutingService.ativarRecebimentos(any())).thenReturn(resposta("AGUARDANDO_APROVACAO"));

        mockMvc.perform(ativar(token("MASTER", ESCOLA), CADASTRO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"))
                .andExpect(jsonPath("$.contaFinal").value("6789"))
                .andExpect(jsonPath("$.credencialCifrada").doesNotExist())
                .andExpect(jsonPath("$.agencia").doesNotExist());
    }

    @Test
    void cadastroInvalidoE400SemChamarORoteador() throws Exception {
        mockMvc.perform(ativar(token("MASTER", ESCOLA), CADASTRO.replace("30110000", "301")))
                .andExpect(status().isBadRequest());
        verify(paymentRoutingService, never()).ativarRecebimentos(any());
    }

    @Test
    void contaJaCriadaE409ComCodigo() throws Exception {
        when(paymentRoutingService.ativarRecebimentos(any()))
                .thenThrow(ContaRecebimentoNaoAtivavelException.jaCriada());

        mockMvc.perform(ativar(token("MASTER", ESCOLA), CADASTRO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTA_JA_CRIADA"));
    }

    @Test
    void recusaDoProvedorE400ComAMensagem() throws Exception {
        when(paymentRoutingService.ativarRecebimentos(any()))
                .thenThrow(new ProvedorRecusouException("O CNPJ informado ja esta em uso."));

        mockMvc.perform(ativar(token("MASTER", ESCOLA), CADASTRO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PROVEDOR_RECUSOU"))
                .andExpect(jsonPath("$.detail").value("O CNPJ informado ja esta em uso."));
    }

    @Test
    void masterSincroniza() throws Exception {
        when(paymentRoutingService.sincronizarConta()).thenReturn(resposta("APROVADA"));

        mockMvc.perform(comToken(post(CONTA + "/sincronizar"), token("MASTER", ESCOLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"));
    }
}
