package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.AcessoService;
import br.pucminas.eduka.backend.service.AlunoService;

/**
 * Contrato de seguranca de {@code /portal/aluno}, ponta a ponta: filter
 * chain, tokens, {@code @PreAuthorize} e servico reais; so os repositorios
 * e o acesso (que a leitura nao usa) sao mock.
 */
@WebMvcTest(PortalAlunoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        AlunoService.class })
class PortalAlunoControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String URL = "/portal/aluno/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private PessoaPapelRepository pessoaPapelRepository;
    @MockitoBean
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @MockitoBean
    private AcessoService acessoService;

    private String accessToken(UUID usuarioId, String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(usuarioId, perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soAlunoAcessa() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR")) {
            mockMvc.perform(com(get(URL), accessToken(UUID.randomUUID(), perfil, ESCOLA_A)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(com(get(URL), accessToken(UUID.randomUUID(), "SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
        verify(alunoRepository, never()).findByUsuarioIdAndEscolaId(any(), any());
    }

    @Test
    void devolveOsDadosDoAlunoDoToken() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana")
                .cpf("52998224725").email("ana@escola.com").dataNascimento("2010-03-15")
                .cidade("Belo Horizonte").ativo(true).build();
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoa.getId())
                .usuarioId(usuarioId).matricula("2026-001").build();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(aluno));
        when(pessoaRepository.findByIdAndEscolaId(pessoa.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoa));

        mockMvc.perform(com(get(URL), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aluno.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Ana"))
                .andExpect(jsonPath("$.matricula").value("2026-001"))
                .andExpect(jsonPath("$.dataNascimento").value("2010-03-15"))
                .andExpect(jsonPath("$.endereco.cidade").value("Belo Horizonte"))
                .andExpect(jsonPath("$.responsaveis.length()").value(0));

        verify(alunoRepository, never()).findAll();
        verify(alunoRepository, never()).findByIdAndEscolaId(any(), any());
    }

    @Test
    void loginSemCadastroDeAlunoResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.empty());

        mockMvc.perform(com(get(URL), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aluno nao encontrado"));
    }
}
