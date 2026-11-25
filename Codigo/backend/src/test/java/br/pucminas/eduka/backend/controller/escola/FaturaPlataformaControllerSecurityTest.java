package br.pucminas.eduka.backend.controller.escola;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
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

import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.comum.Ordenacao;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.escola.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.financeiro.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.financeiro.PixResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.escola.FaturaDuplicadaException;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaJaPagaException;
import br.pucminas.eduka.backend.exception.financeiro.ComprovanteIndisponivelException;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * Contrato das faturas da plataforma (spec §10.1 F1 a F7): lancar, listar e
 * cancelar so SUPER_ADMIN; ver e pagar so MASTER. As regras ficam no
 * roteador, que aqui e mock.
 */
@WebMvcTest(FaturaPlataformaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class FaturaPlataformaControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID FATURA = UUID.randomUUID();
    private static final String DA_ESCOLA = "/escolas/" + ESCOLA + "/faturas";
    private static final String MINHAS = "/config-pagamento/faturas";
    private static final String LANCAMENTO = "{\"competencia\":\"2026-11\",\"descricao\":\"Eduka novembro\","
            + "\"valor\":299.90,\"dataVencimento\":\"2026-11-10\"}";

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

    private MockHttpServletRequestBuilder lancar(String token, String corpo) {
        return comToken(post(DA_ESCOLA), token).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private MockHttpServletRequestBuilder masterPaga() {
        return comToken(post(MINHAS + "/" + FATURA + "/pagar"), token("MASTER", ESCOLA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"forma\":\"PIX\"}");
    }

    private static FaturaPlataformaResponse resposta(List<String> acoes) {
        return new FaturaPlataformaResponse(FATURA, ESCOLA, "Colegio Horizonte", "2026-11", "Eduka novembro",
                new BigDecimal("299.90"), LocalDate.of(2026, 11, 10), "PENDENTE", "AGUARDANDO_PAGAMENTO", null, null,
                acoes);
    }

    // ---- Super Admin ----

    @Test
    void soSuperAdminLancaEListaFaturasDeEscola() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(lancar(token(perfil, ESCOLA), LANCAMENTO)).andExpect(status().isForbidden());
            mockMvc.perform(comToken(get(DA_ESCOLA), token(perfil, ESCOLA))).andExpect(status().isForbidden());
        }
        verify(paymentRoutingService, never()).lancarFatura(any(), any());
        verify(paymentRoutingService, never()).listarFaturas(any());
    }

    @Test
    void soSuperAdminListaAsFaturasDeTodasAsEscolas() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(comToken(get("/escolas/faturas"), token(perfil, ESCOLA))).andExpect(status().isForbidden());
        }
        when(paymentRoutingService.listarFaturasDeTodasAsEscolas())
                .thenReturn(List.of(resposta(List.of(FaturaPlataformaResponse.CANCELAR))));

        mockMvc.perform(comToken(get("/escolas/faturas"), token("SUPER_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].escolaNome").value("Colegio Horizonte"));
    }

    @Test
    void lancarDevolve201ComAsAcoes() throws Exception {
        when(paymentRoutingService.lancarFatura(any(), any()))
                .thenReturn(resposta(List.of(FaturaPlataformaResponse.CANCELAR)));

        mockMvc.perform(lancar(token("SUPER_ADMIN", null), LANCAMENTO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.competencia").value("2026-11"))
                .andExpect(jsonPath("$.acoes[0]").value("CANCELAR"));
    }

    @Test
    void competenciaForaDoFormatoE400SemChamarORoteador() throws Exception {
        mockMvc.perform(lancar(token("SUPER_ADMIN", null), LANCAMENTO.replace("2026-11\"", "11/2026\"")))
                .andExpect(status().isBadRequest());
        verify(paymentRoutingService, never()).lancarFatura(any(), any());
    }

    @Test
    void faturaDuplicadaE409ComCodigo() throws Exception {
        when(paymentRoutingService.lancarFatura(any(), any())).thenThrow(new FaturaDuplicadaException());

        mockMvc.perform(lancar(token("SUPER_ADMIN", null), LANCAMENTO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FATURA_DUPLICADA"));
    }

    @Test
    void cancelarFaturaJaPagaE409ComCodigo() throws Exception {
        when(paymentRoutingService.cancelarFatura(ESCOLA, FATURA)).thenThrow(new CobrancaJaPagaException());

        mockMvc.perform(comToken(patch(DA_ESCOLA + "/" + FATURA + "/cancelar"), token("SUPER_ADMIN", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COBRANCA_JA_PAGA"));
    }

    @Test
    void masterNaoCancela() throws Exception {
        mockMvc.perform(comToken(patch(DA_ESCOLA + "/" + FATURA + "/cancelar"), token("MASTER", ESCOLA)))
                .andExpect(status().isForbidden());
        verify(paymentRoutingService, never()).cancelarFatura(any(), any());
    }

    // ---- MASTER ----

    @Test
    void soMasterVeEPagaAsFaturasDaEscola() throws Exception {
        for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(comToken(get(MINHAS), token(perfil, ESCOLA))).andExpect(status().isForbidden());
        }
        mockMvc.perform(comToken(get(MINHAS), token("SUPER_ADMIN", null))).andExpect(status().isForbidden());
        mockMvc.perform(comToken(post(MINHAS + "/" + FATURA + "/pagar"), token("SUPER_ADMIN", null))
                .contentType(MediaType.APPLICATION_JSON).content("{\"forma\":\"PIX\"}"))
                .andExpect(status().isForbidden());
        verify(paymentRoutingService, never()).pagarFatura(any(), any());
    }

    @Test
    void masterListaAsFaturasComOPagar() throws Exception {
        when(paymentRoutingService.listarMinhasFaturas())
                .thenReturn(List.of(resposta(List.of(FaturaPlataformaResponse.PAGAR))));

        mockMvc.perform(comToken(get(MINHAS), token("MASTER", ESCOLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].acoes[0]").value("PAGAR"));
    }

    @Test
    void paginaDeFaturasAceitaMaisDeUmaSituacao() throws Exception {
        PaginaResponse<FaturaPlataformaResponse> vazia = new PaginaResponse<>(List.of(), 0, 10, 0, 0);
        when(paymentRoutingService.listarMinhasFaturas(any(), any(), any(), any())).thenReturn(vazia);

        mockMvc.perform(comToken(get(MINHAS + "/pagina?status=PAGA,CANCELADA&ordenarPor=COMPETENCIA"),
                token("MASTER", ESCOLA))).andExpect(status().isOk());
        verify(paymentRoutingService).listarMinhasFaturas(eq(EnumSet.of(StatusCobranca.PAGA,
                StatusCobranca.CANCELADA)), any(), eq(Ordenacao.Faturas.COMPETENCIA), any());

        mockMvc.perform(comToken(get(MINHAS + "/pagina?status=PAGA,QUITADA"), token("MASTER", ESCOLA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void masterBuscaUmaFaturaSoDaPropriaEscola() throws Exception {
        for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(comToken(get(MINHAS + "/" + FATURA), token(perfil, ESCOLA)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(comToken(get(MINHAS + "/" + FATURA), token("SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
        verify(paymentRoutingService, never()).buscarMinhaFatura(any());

        when(paymentRoutingService.buscarMinhaFatura(FATURA))
                .thenReturn(resposta(List.of(FaturaPlataformaResponse.PAGAR)));
        mockMvc.perform(comToken(get(MINHAS + "/" + FATURA), token("MASTER", ESCOLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(FATURA.toString()))
                .andExpect(jsonPath("$.situacaoPagamento").value("AGUARDANDO_PAGAMENTO"));

        UUID deOutraEscola = UUID.randomUUID();
        when(paymentRoutingService.buscarMinhaFatura(deOutraEscola))
                .thenThrow(new RecursoNaoEncontradoException("Fatura nao encontrada"));
        mockMvc.perform(comToken(get(MINHAS + "/" + deOutraEscola), token("MASTER", ESCOLA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void pagarProntoDevolve200EProcessandoDevolve202() throws Exception {
        when(paymentRoutingService.pagarFatura(FATURA, FormaPagamento.PIX)).thenReturn(DadosPagamentoResponse
                .comPix(new BigDecimal("299.90"), LocalDate.of(2026, 11, 10), new PixResponse("aW1n", "000201", null)));
        mockMvc.perform(masterPaga())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pix.copiaECola").value("000201"));

        when(paymentRoutingService.pagarFatura(FATURA, FormaPagamento.PIX)).thenReturn(DadosPagamentoResponse
                .emProcessamento(FormaPagamento.PIX, new BigDecimal("299.90"), LocalDate.of(2026, 11, 10)));
        mockMvc.perform(masterPaga())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.situacao").value("PROCESSANDO"));
    }

    @Test
    void comprovanteAntesDePagarE404ComCodigo() throws Exception {
        when(paymentRoutingService.comprovanteMinhaFatura(FATURA)).thenThrow(new ComprovanteIndisponivelException());

        mockMvc.perform(comToken(get(MINHAS + "/" + FATURA + "/comprovante"), token("MASTER", ESCOLA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("COMPROVANTE_INDISPONIVEL"));
    }
}
