package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
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

/**
 * Contrato de {@code /alunos/{id}/financeiro}: filter chain, tokens,
 * {@code @PreAuthorize} e servico reais; so os repositorios sao mock.
 */
@WebMvcTest(AlunoFinanceiroController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        CobrancaService.class })
class AlunoFinanceiroControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CobrancaRepository cobrancaRepository;
    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private MatriculaTurmaRepository matriculaRepository;
    @MockitoBean
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private AvisoCobrancaService avisoCobrancaService;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private PagamentoCobrancaRepository pagamentoRepository;
    @MockitoBean
    private PaymentRoutingService paymentRoutingService;
    @MockitoBean
    private PlatformTransactionManager transactionManager;

    private Pessoa pessoaAna;
    private Aluno ana;

    @BeforeEach
    void dados() {
        pessoaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        ana = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoaAna.getId())
                .matricula("2026001").build();
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));
    }

    private String token(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String url(Aluno aluno) {
        return "/alunos/" + aluno.getId() + "/financeiro";
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(url(ana))).andExpect(status().isUnauthorized());
    }

    @Test
    void soGestaoEFinanceiroAcessam() throws Exception {
        for (String perfil : List.of("SECRETARIA", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(com(get(url(ana)), token(perfil, ESCOLA_A))).andExpect(status().isForbidden());
        }
        mockMvc.perform(com(get(url(ana)), token("SUPER_ADMIN", null))).andExpect(status().isForbidden());
        verify(cobrancaRepository, never()).findAllByEscolaIdAndAlunoId(any(), any());

        for (String perfil : List.of("MASTER", "FINANCEIRO")) {
            mockMvc.perform(com(get(url(ana)), token(perfil, ESCOLA_A))).andExpect(status().isOk());
        }
    }

    @Test
    void devolveResumoECobrancasDoAluno() throws Exception {
        when(cobrancaRepository.findAllByEscolaIdAndAlunoId(ESCOLA_A, ana.getId())).thenReturn(List.of(
                Cobranca.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(ana).descricao("Marco")
                        .valor(new BigDecimal("450.00")).dataVencimento(LocalDate.of(2099, 3, 10))
                        .status(StatusCobranca.PENDENTE).build()));

        mockMvc.perform(com(get(url(ana)), token("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alunoNome").value("Ana"))
                .andExpect(jsonPath("$.alunoNumeroMatricula").value("2026001"))
                .andExpect(jsonPath("$.resumo.valorEmAberto").value(450.00))
                .andExpect(jsonPath("$.resumo.valorEmAtraso").value(0))
                .andExpect(jsonPath("$.cobrancas.length()").value(1))
                .andExpect(jsonPath("$.cobrancas[0].status").value("PENDENTE"));
    }

    @Test
    void alunoSemCobrancaTemHistoricoVazio() throws Exception {
        when(cobrancaRepository.findAllByEscolaIdAndAlunoId(ESCOLA_A, ana.getId())).thenReturn(List.of());

        mockMvc.perform(com(get(url(ana)), token("MASTER", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobrancas.length()").value(0))
                .andExpect(jsonPath("$.resumo.valorEmAberto").value(0));
    }

    @Test
    void alunoDeOutraEscolaResponde404() throws Exception {
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_B)).thenReturn(Optional.empty());

        mockMvc.perform(com(get(url(ana)), token("FINANCEIRO", ESCOLA_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aluno nao encontrado"));
        verify(cobrancaRepository, never()).findAllByEscolaIdAndAlunoId(any(), any());
    }

    @Test
    void cobrancaPagaTrazOPagamentoRegistrado() throws Exception {
        Cobranca paga = Cobranca.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(ana).descricao("Fevereiro")
                .valor(new BigDecimal("450.00")).dataVencimento(LocalDate.of(2026, 2, 10))
                .status(StatusCobranca.PAGA).build();
        Cobranca aberta = Cobranca.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(ana).descricao("Marco")
                .valor(new BigDecimal("450.00")).dataVencimento(LocalDate.of(2099, 3, 10))
                .status(StatusCobranca.PENDENTE).build();
        when(cobrancaRepository.findAllByEscolaIdAndAlunoId(ESCOLA_A, ana.getId())).thenReturn(List.of(paga, aberta));
        when(pagamentoRepository.findAllByEscolaIdAndCobrancaIdIn(ESCOLA_A, List.of(paga.getId()))).thenReturn(List.of(
                PagamentoCobranca.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).cobrancaId(paga.getId())
                        .valorPago(new BigDecimal("450.00")).dataPagamento(LocalDate.of(2026, 2, 8))
                        .formaPagamento(FormaPagamento.PIX).origem(OrigemPagamento.ONLINE).build()));

        mockMvc.perform(com(get(url(ana)), token("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobrancas[0].descricao").value("Marco"))
                .andExpect(jsonPath("$.cobrancas[0].pagamento").doesNotExist())
                .andExpect(jsonPath("$.cobrancas[1].pagamento.origem").value("ONLINE"))
                .andExpect(jsonPath("$.cobrancas[1].pagamento.forma").value("PIX"))
                .andExpect(jsonPath("$.cobrancas[1].pagamento.dataPagamento").value("2026-02-08"))
                .andExpect(jsonPath("$.cobrancas[1].pagamento.valorPago").value(450.00))
                // A escola abre o comprovante pela rota dela, nao pela do portal.
                .andExpect(jsonPath("$.cobrancas[1].pagamento.comprovanteUrl")
                        .value("/cobrancas/" + paga.getId() + "/comprovante"));
    }

    @Test
    void baixaManualApareceComoPagamentoSemComprovante() throws Exception {
        Cobranca paga = Cobranca.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(ana).descricao("Fevereiro")
                .valor(new BigDecimal("450.00")).dataVencimento(LocalDate.of(2026, 2, 10))
                .status(StatusCobranca.PAGA).pagaEm(Instant.parse("2026-02-09T15:00:00Z")).build();
        when(cobrancaRepository.findAllByEscolaIdAndAlunoId(ESCOLA_A, ana.getId())).thenReturn(List.of(paga));
        when(pagamentoRepository.findAllByEscolaIdAndCobrancaIdIn(ESCOLA_A, List.of(paga.getId())))
                .thenReturn(List.of());

        mockMvc.perform(com(get(url(ana)), token("MASTER", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobrancas[0].pagamento.origem").value("MANUAL"))
                .andExpect(jsonPath("$.cobrancas[0].pagamento.valorPago").value(450.00))
                .andExpect(jsonPath("$.cobrancas[0].pagamento.dataPagamento").value("2026-02-09"))
                .andExpect(jsonPath("$.cobrancas[0].pagamento.forma").doesNotExist())
                .andExpect(jsonPath("$.cobrancas[0].pagamento.comprovanteUrl").doesNotExist());
    }
}
