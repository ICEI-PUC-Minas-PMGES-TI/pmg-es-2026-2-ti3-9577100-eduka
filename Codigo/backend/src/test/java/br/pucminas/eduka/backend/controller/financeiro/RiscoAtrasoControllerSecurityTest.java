package br.pucminas.eduka.backend.controller.financeiro;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.financeiro.RiscoAtrasoService;

/**
 * Contrato do risco de atraso: filter chain, tokens, {@code @PreAuthorize} e
 * servico reais; so os repositorios sao mock. As contas ficam em
 * {@code RegrasRiscoAtrasoTest} e {@code RiscoAtrasoBancoRealTest}.
 */
@WebMvcTest(RiscoAtrasoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        RiscoAtrasoService.class })
class RiscoAtrasoControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();
    private static final String DA_ESCOLA = "/cobrancas/risco-atraso";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CobrancaRepository cobrancaRepository;
    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;

    private Aluno ana;

    @BeforeEach
    void dados() {
        Pessoa pessoaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        ana = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoaAna.getId())
                .matricula("2026001").build();
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(cobrancaRepository.buscarParaRiscoDoAluno(eq(ESCOLA_A), eq(ana.getId()), any())).thenReturn(List.of());
        when(cobrancaRepository.buscarParaRisco(eq(ESCOLA_A), any())).thenReturn(List.of());
    }

    private String token(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String doAluno(Aluno aluno) {
        return "/alunos/" + aluno.getId() + "/financeiro/risco";
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(doAluno(ana))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(DA_ESCOLA)).andExpect(status().isUnauthorized());
    }

    @Test
    void soGestaoEFinanceiroAcessam() throws Exception {
        for (String url : List.of(doAluno(ana), DA_ESCOLA)) {
            for (String perfil : List.of("SECRETARIA", "PROFESSOR", "ALUNO")) {
                mockMvc.perform(com(get(url), token(perfil, ESCOLA_A))).andExpect(status().isForbidden());
            }
            mockMvc.perform(com(get(url), token("SUPER_ADMIN", null))).andExpect(status().isForbidden());
            for (String perfil : List.of("MASTER", "FINANCEIRO")) {
                mockMvc.perform(com(get(url), token(perfil, ESCOLA_A))).andExpect(status().isOk());
            }
        }
        verify(cobrancaRepository, never()).buscarParaRisco(eq(ESCOLA_B), any());
    }

    @Test
    void alunoSemCobrancaTemRiscoBaixoComMotivo() throws Exception {
        mockMvc.perform(com(get(doAluno(ana)), token("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alunoNome").value("Ana"))
                .andExpect(jsonPath("$.alunoNumeroMatricula").value("2026001"))
                .andExpect(jsonPath("$.nivel").value("BAIXO"))
                .andExpect(jsonPath("$.pontos").value(0))
                .andExpect(jsonPath("$.motivo").value("Sem cobranças vencidas nos últimos 12 meses"))
                .andExpect(jsonPath("$.fatores.length()").value(3))
                .andExpect(jsonPath("$.fatores[0].regra").value("ATRASOS_ANTERIORES"))
                .andExpect(jsonPath("$.indicadores.valorEmAtraso").value(0));
    }

    @Test
    void alunoDeOutraEscolaResponde404() throws Exception {
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_B)).thenReturn(Optional.empty());

        mockMvc.perform(com(get(doAluno(ana)), token("FINANCEIRO", ESCOLA_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aluno nao encontrado"));
        verify(cobrancaRepository, never()).buscarParaRiscoDoAluno(any(), any(), any());
    }

    @Test
    void nivelInvalidoResponde400() throws Exception {
        mockMvc.perform(com(get(DA_ESCOLA).param("nivelMinimo", "ALTISSIMO"), token("MASTER", ESCOLA_A)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void escolaSemCobrancaDevolveListaVazia() throws Exception {
        mockMvc.perform(com(get(DA_ESCOLA).param("nivelMinimo", "MEDIO"), token("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
