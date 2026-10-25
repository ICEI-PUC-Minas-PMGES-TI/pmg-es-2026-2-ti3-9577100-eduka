package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
import org.springframework.transaction.PlatformTransactionManager;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
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
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.AuditoriaService;
import br.pucminas.eduka.backend.service.AvisoCobrancaService;
import br.pucminas.eduka.backend.service.CobrancaService;
import br.pucminas.eduka.backend.exception.BoletoNaoGeradoException;

/**
 * Contrato de {@code /portal/aluno/cobrancas} (spec §10, P2 e P3): so ALUNO,
 * status por situacao e o {@code codigo} de cada recusa. As regras do pagar
 * ficam no roteador, que aqui e mock; a consulta usa o servico real.
 */
@WebMvcTest(PagamentoAlunoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        CobrancaService.class })
class PagamentoAlunoControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID COBRANCA = UUID.randomUUID();
    private static final String CONSULTA = "/portal/aluno/cobrancas/" + COBRANCA;
    private static final String PAGAR = CONSULTA + "/pagar";
    private static final BigDecimal VALOR = new BigDecimal("500.00");
    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 10, 25);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PaymentRoutingService paymentRoutingService;
    @MockitoBean
    private CobrancaRepository cobrancaRepository;
    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private MatriculaTurmaRepository matriculaRepository;
    @MockitoBean
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private PagamentoCobrancaRepository pagamentoRepository;
    @MockitoBean
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private AvisoCobrancaService avisoCobrancaService;

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

    // ---- P2: GET /portal/aluno/cobrancas/{id} ----

    @Test
    void consultaSemTokenResponde401ESoAlunoConsulta() throws Exception {
        mockMvc.perform(get(CONSULTA)).andExpect(status().isUnauthorized());
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR")) {
            mockMvc.perform(get(CONSULTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(perfil, ESCOLA)))
                    .andExpect(status().isForbidden());
        }
        verify(cobrancaRepository, never()).findByIdAndEscolaId(any(), any());
    }

    @Test
    void alunoVeASituacaoDoPagamentoESemNadaDoProvedor() throws Exception {
        UUID usuario = UUID.randomUUID();
        Aluno aluno = alunoDoLogin(usuario);
        Cobranca cobranca = Cobranca.builder().id(COBRANCA).escolaId(ESCOLA).aluno(aluno).descricao("Mensalidade")
                .valor(VALOR).dataVencimento(LocalDate.of(2099, 10, 25)).status(StatusCobranca.PENDENTE)
                .situacaoProvedor(SituacaoProvedor.EMITIDA).formaPagamento(FormaPagamento.PIX)
                .idCobrancaExterna("pay_secreto").build();
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.of(cobranca));

        mockMvc.perform(get(CONSULTA).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtService.generateAccessToken(usuario, "ALUNO", ESCOLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COBRANCA.toString()))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.situacaoPagamento").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.formaPagamento").value("PIX"))
                .andExpect(jsonPath("$.acoes[0]").value("PAGAR"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("pay_secreto"))));
    }

    @Test
    void cobrancaDeOutroAlunoNaConsultaResponde404() throws Exception {
        UUID usuario = UUID.randomUUID();
        alunoDoLogin(usuario);
        Aluno outro = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).build();
        when(cobrancaRepository.findByIdAndEscolaId(COBRANCA, ESCOLA)).thenReturn(Optional.of(Cobranca.builder()
                .id(COBRANCA).escolaId(ESCOLA).aluno(outro).descricao("Mensalidade").valor(VALOR)
                .dataVencimento(LocalDate.of(2099, 10, 25)).status(StatusCobranca.PENDENTE).build()));

        mockMvc.perform(get(CONSULTA).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtService.generateAccessToken(usuario, "ALUNO", ESCOLA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cobranca nao encontrada"));
    }

    private Aluno alunoDoLogin(UUID usuario) {
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).usuarioId(usuario).build();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuario, ESCOLA)).thenReturn(Optional.of(aluno));
        return aluno;
    }

    private void recusa(RuntimeException erro, int status, String codigo) throws Exception {
        doThrow(erro).when(paymentRoutingService).pagarCobranca(COBRANCA, FormaPagamento.PIX);

        mockMvc.perform(alunoPaga("PIX"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.codigo").value(codigo));
    }

    // ---- PDF do boleto ----

    private String urlBoleto() {
        return "/portal/aluno/cobrancas/" + COBRANCA + "/boleto";
    }

    @Test
    void boletoDevolveOPdfComoAnexoSemCache() throws Exception {
        byte[] pdf = "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(paymentRoutingService.baixarBoleto(COBRANCA)).thenReturn(pdf);

        mockMvc.perform(get(urlBoleto()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ALUNO", ESCOLA)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void soAlunoBaixaOBoleto() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR")) {
            mockMvc.perform(get(urlBoleto()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(perfil, ESCOLA)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(urlBoleto())).andExpect(status().isUnauthorized());
        verify(paymentRoutingService, never()).baixarBoleto(any());
    }

    @Test
    void boletoAindaNaoGeradoResponde409ComCodigo() throws Exception {
        when(paymentRoutingService.baixarBoleto(COBRANCA)).thenThrow(new BoletoNaoGeradoException());

        mockMvc.perform(get(urlBoleto()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ALUNO", ESCOLA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BOLETO_NAO_GERADO"));
    }
}
