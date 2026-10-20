package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.dto.BoletoResponse;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.PixResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.exception.CobrancaNaoPagavelException;
import br.pucminas.eduka.backend.exception.PagadorSemCpfException;
import br.pucminas.eduka.backend.exception.PagamentoEmProcessamentoException;
import br.pucminas.eduka.backend.exception.RecebimentosIndisponiveisException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.pagamento.ProvedorIndisponivelException;
import br.pucminas.eduka.backend.pagamento.ProvedorRecusouException;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * Contrato de {@code POST /portal/aluno/cobrancas/{id}/pagar} (P3): so ALUNO,
 * status por situacao e o {@code codigo} de cada recusa (spec §10). As regras
 * ficam no roteador, que aqui e mock.
 */
@WebMvcTest(PagamentoAlunoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class PagamentoAlunoControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID COBRANCA = UUID.randomUUID();
    private static final String PAGAR = "/portal/aluno/cobrancas/" + COBRANCA + "/pagar";
    private static final BigDecimal VALOR = new BigDecimal("500.00");
    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 10, 25);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PaymentRoutingService paymentRoutingService;

    private String token(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder pagar(String token, String corpo) {
        return post(PAGAR).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private MockHttpServletRequestBuilder alunoPaga(String forma) {
        return pagar(token("ALUNO", ESCOLA), "{\"forma\":\"" + forma + "\"}");
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(post(PAGAR).contentType(MediaType.APPLICATION_JSON).content("{\"forma\":\"PIX\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soAlunoPaga() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR")) {
            mockMvc.perform(pagar(token(perfil, ESCOLA), "{\"forma\":\"PIX\"}")).andExpect(status().isForbidden());
        }
        mockMvc.perform(pagar(token("SUPER_ADMIN", null), "{\"forma\":\"PIX\"}")).andExpect(status().isForbidden());
        verify(paymentRoutingService, never()).pagarCobranca(any(), any());
    }

    @Test
    void pixProntoDevolve200ComQrCodeECopiaECola() throws Exception {
        OffsetDateTime validade = OffsetDateTime.of(2027, 10, 25, 23, 59, 59, 0, ZoneOffset.ofHours(-3));
        when(paymentRoutingService.pagarCobranca(COBRANCA, FormaPagamento.PIX)).thenReturn(DadosPagamentoResponse
                .comPix(VALOR, VENCIMENTO, new PixResponse("aW1hZ2Vt", "00020101021226", validade)));

        mockMvc.perform(alunoPaga("PIX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PRONTO"))
                .andExpect(jsonPath("$.forma").value("PIX"))
                .andExpect(jsonPath("$.valor").value(500.00))
                .andExpect(jsonPath("$.vencimento").value("2026-10-25"))
                .andExpect(jsonPath("$.pix.qrCodeBase64").value("aW1hZ2Vt"))
                .andExpect(jsonPath("$.pix.copiaECola").value("00020101021226"))
                .andExpect(jsonPath("$.boleto").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString("asaas"))));
    }

    @Test
    void boletoProntoDevolveALinhaDigitavel() throws Exception {
        when(paymentRoutingService.pagarCobranca(COBRANCA, FormaPagamento.BOLETO)).thenReturn(DadosPagamentoResponse
                .comBoleto(VALOR, VENCIMENTO, new BoletoResponse("23793381286000000000300000000401", "2379100")));

        mockMvc.perform(alunoPaga("BOLETO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forma").value("BOLETO"))
                .andExpect(jsonPath("$.boleto.linhaDigitavel").value("23793381286000000000300000000401"))
                .andExpect(jsonPath("$.pix").doesNotExist());
    }

    @Test
    void processandoDevolve202SemDadosDePagamento() throws Exception {
        when(paymentRoutingService.pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .thenReturn(DadosPagamentoResponse.emProcessamento(FormaPagamento.PIX, VALOR, VENCIMENTO));

        mockMvc.perform(alunoPaga("PIX"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.situacao").value("PROCESSANDO"))
                .andExpect(jsonPath("$.pix").doesNotExist());
    }

    @Test
    void formaAusenteOuForaDaListaResponde400SemChamarORoteador() throws Exception {
        mockMvc.perform(pagar(token("ALUNO", ESCOLA), "{}")).andExpect(status().isBadRequest());
        mockMvc.perform(alunoPaga("CARTAO")).andExpect(status().isBadRequest());
        verify(paymentRoutingService, never()).pagarCobranca(any(), any());
    }

    @Test
    void cadaRecusaTemSeuStatusECodigo() throws Exception {
        recusa(new CobrancaNaoPagavelException(), 409, "COBRANCA_NAO_PAGAVEL");
        recusa(new RecebimentosIndisponiveisException("NAO_INICIADA"), 409, "RECEBIMENTOS_INDISPONIVEIS");
        recusa(new PagamentoEmProcessamentoException(), 409, "PAGAMENTO_EM_PROCESSAMENTO");
        recusa(new PagadorSemCpfException(), 400, "PAGADOR_SEM_CPF");
        recusa(new ProvedorRecusouException("Valor minimo nao atingido"), 400, "PROVEDOR_RECUSOU");
        recusa(new ProvedorIndisponivelException("timeout"), 503, "PROVEDOR_INDISPONIVEL");
    }

    @Test
    void cobrancaDeOutroAlunoResponde404() throws Exception {
        when(paymentRoutingService.pagarCobranca(COBRANCA, FormaPagamento.PIX))
                .thenThrow(new RecursoNaoEncontradoException("Cobranca nao encontrada"));

        mockMvc.perform(alunoPaga("PIX"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cobranca nao encontrada"));
    }

    private void recusa(RuntimeException erro, int status, String codigo) throws Exception {
        doThrow(erro).when(paymentRoutingService).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        mockMvc.perform(alunoPaga("PIX"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.codigo").value(codigo));
    }
}
