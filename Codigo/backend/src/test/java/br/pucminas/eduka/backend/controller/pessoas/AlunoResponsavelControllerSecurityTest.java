package br.pucminas.eduka.backend.controller.pessoas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Responsavel;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ResponsavelRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.pessoas.ResponsavelService;

/**
 * Contrato de {@code POST /alunos/{id}/responsaveis}: filter chain, tokens,
 * {@code @PreAuthorize} e servico reais; so os repositorios sao mock.
 */
@WebMvcTest(AlunoResponsavelController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        ResponsavelService.class })
class AlunoResponsavelControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();
    private static final String COM_CPF = """
            {"nome":"Marta","cpf":"52998224725","parentesco":"MAE","financeiro":true}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ResponsavelRepository responsavelRepository;
    @MockitoBean
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private PessoaPapelRepository pessoaPapelRepository;
    @MockitoBean
    private CobrancaRepository cobrancaRepository;

    private Aluno ana;

    @BeforeEach
    void dados() {
        ana = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(UUID.randomUUID())
                .matricula("2026001").build();
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(responsavelRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of());
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(responsavelRepository.save(any(Responsavel.class))).thenAnswer(inv -> {
            Responsavel responsavel = inv.getArgument(0);
            responsavel.setId(UUID.randomUUID());
            return responsavel;
        });
    }

    private MockHttpServletRequestBuilder incluir(String perfil, UUID escolaId, String corpo) {
        return post("/alunos/" + ana.getId() + "/responsaveis")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(post("/alunos/" + ana.getId() + "/responsaveis").contentType(MediaType.APPLICATION_JSON)
                .content(COM_CPF)).andExpect(status().isUnauthorized());
    }

    @Test
    void professorEAlunoNaoIncluem() throws Exception {
        for (String perfil : List.of("PROFESSOR", "ALUNO")) {
            mockMvc.perform(incluir(perfil, ESCOLA_A, COM_CPF)).andExpect(status().isForbidden());
        }
        verify(alunoResponsavelRepository, never()).save(any());
    }

    @Test
    void secretariaIncluiOFinanceiro() throws Exception {
        mockMvc.perform(incluir("SECRETARIA", ESCOLA_A, COM_CPF))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nome").value("Marta"))
                .andExpect(jsonPath("$.financeiro").value(true));
    }

    @Test
    void financeiroSemCpfResponde400() throws Exception {
        mockMvc.perform(incluir("SECRETARIA", ESCOLA_A, """
                {"nome":"Marta","parentesco":"MAE","financeiro":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Responsavel financeiro precisa de CPF valido"));
        verify(alunoResponsavelRepository, never()).save(any());
    }

    @Test
    void parentescoAcimaDoLimiteResponde400() throws Exception {
        mockMvc.perform(incluir("SECRETARIA", ESCOLA_A, """
                {"nome":"Marta","parentesco":"%s"}""".formatted("x".repeat(31))))
                .andExpect(status().isBadRequest());
        verify(alunoResponsavelRepository, never()).save(any());
    }

    @Test
    void alunoDeOutraEscolaResponde404() throws Exception {
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_B)).thenReturn(Optional.empty());

        mockMvc.perform(incluir("MASTER", ESCOLA_B, COM_CPF)).andExpect(status().isNotFound());
        verify(pessoaRepository, never()).save(any());
    }
}
