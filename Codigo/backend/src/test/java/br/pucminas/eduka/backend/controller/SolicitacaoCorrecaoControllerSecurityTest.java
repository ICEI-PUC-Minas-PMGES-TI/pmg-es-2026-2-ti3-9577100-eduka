package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.StatusSolicitacaoCorrecao;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.SolicitacaoCorrecaoRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.SolicitacaoCorrecaoService;

/**
 * Quem pede correcao de dados e quem atende, ponta a ponta: filter chain,
 * tokens, {@code @PreAuthorize} e servico reais; so os repositorios sao mock.
 */
@WebMvcTest(SolicitacaoCorrecaoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        SolicitacaoCorrecaoService.class })
class SolicitacaoCorrecaoControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String CORPO_VALIDO = "{\"mensagem\":\"O CPF esta errado\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private SolicitacaoCorrecaoRepository solicitacaoRepository;
    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;

    private String accessToken(String perfil) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, "SUPER_ADMIN".equals(perfil) ? null : ESCOLA_A);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder pedido(String token) {
        return com(post("/portal/aluno/correcoes"), token).contentType(MediaType.APPLICATION_JSON)
                .content(CORPO_VALIDO);
    }

    @Test
    void semTokenResponde401EmTodasAsRotas() throws Exception {
        mockMvc.perform(post("/portal/aluno/correcoes").contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/portal/aluno/correcoes/aberta")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/alunos/correcoes")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/alunos/correcoes/" + UUID.randomUUID() + "/atender"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(solicitacaoRepository);
    }

    @Test
    void soOAlunoPedeCorrecao() throws Exception {
        for (String perfil : List.of("SUPER_ADMIN", "MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR")) {
            String token = accessToken(perfil);
            mockMvc.perform(pedido(token)).andExpect(status().isForbidden());
            mockMvc.perform(com(get("/portal/aluno/correcoes/aberta"), token)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(solicitacaoRepository);
    }

    @Test
    void soGestaoESecretariaVeemEAtendem() throws Exception {
        for (String perfil : List.of("SUPER_ADMIN", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            String token = accessToken(perfil);
            mockMvc.perform(com(get("/alunos/correcoes"), token)).andExpect(status().isForbidden());
            mockMvc.perform(com(patch("/alunos/correcoes/" + UUID.randomUUID() + "/atender"), token))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(solicitacaoRepository);
    }

    @Test
    void secretariaEMasterVeemOsPedidosDaPropriaEscola() throws Exception {
        when(solicitacaoRepository.findAllByEscolaIdAndStatusOrderByCriadoEmAsc(ESCOLA_A,
                StatusSolicitacaoCorrecao.ABERTA)).thenReturn(List.of());

        for (String perfil : List.of("SECRETARIA", "MASTER")) {
            mockMvc.perform(com(get("/alunos/correcoes"), accessToken(perfil))).andExpect(status().isOk());
        }
    }

    @Test
    void pedidoDeOutraEscolaResponde404SemAtender() throws Exception {
        UUID pedidoDeOutraEscola = UUID.randomUUID();
        when(solicitacaoRepository.findByIdAndEscolaId(pedidoDeOutraEscola, ESCOLA_A)).thenReturn(Optional.empty());

        mockMvc.perform(com(patch("/alunos/correcoes/" + pedidoDeOutraEscola + "/atender"),
                accessToken("SECRETARIA")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // A busca vai com a escola do token; nunca so pelo id.
        verify(solicitacaoRepository).findByIdAndEscolaId(pedidoDeOutraEscola, ESCOLA_A);
        verify(solicitacaoRepository, never()).findById(any());
    }
}
