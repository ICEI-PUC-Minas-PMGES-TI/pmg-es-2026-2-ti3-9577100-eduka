package br.pucminas.eduka.backend.controller.academico;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import br.pucminas.eduka.backend.domain.academico.Avaliacao;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.TipoAvaliacao;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.NotaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.academico.AvaliacaoService;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Contrato de seguranca e de erros de {@code /portal/professor/avaliacoes},
 * ponta a ponta: filter chain, tokens, {@code @PreAuthorize}, validacao e
 * servico reais; so os repositorios sao mock.
 */
@WebMvcTest(AvaliacaoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        AvaliacaoService.class })
class AvaliacaoControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final String URL = "/portal/professor/avaliacoes";
    private static final String CORPO_VALIDO = """
            {"turmaId":1,"disciplinaId":10,"tipo":"PROVA","titulo":"Prova 1","data":"2026-10-10","valor":10}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AvaliacaoRepository avaliacaoRepository;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private NotaRepository notaRepository;
    @MockitoBean
    private ProfessorRepository professorRepository;
    @MockitoBean
    private AlocacaoProfessorRepository alocacaoRepository;
    @MockitoBean
    private TurmaRepository turmaRepository;
    @MockitoBean
    private DisciplinaRepository disciplinaRepository;
    @MockitoBean
    private MatriculaTurmaRepository matriculaRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String corpo) {
        return request.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private Professor professorLogado(UUID usuarioId) {
        Professor professor = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA).usuarioId(usuarioId)
                .ativo(true).build();
        when(professorRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA)).thenReturn(Optional.of(professor));
        return professor;
    }

    private String tokenProfessor(UUID usuarioId) {
        return jwtService.generateAccessToken(usuarioId, "PROFESSOR", ESCOLA);
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(URL).param("turmaId", "1").param("disciplinaId", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soProfessorAcessa() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "ALUNO")) {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA);
            mockMvc.perform(com(get(URL).param("turmaId", "1").param("disciplinaId", "10"), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(json(post(URL), CORPO_VALIDO), token))
                    .andExpect(status().isForbidden());
        }
        verify(avaliacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void cadastroEmDisciplinaNaoAlocadaResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId);

        mockMvc.perform(com(json(post(URL), CORPO_VALIDO), tokenProfessor(usuarioId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Turma ou disciplina nao alocada ao professor"));
        verify(avaliacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void cadastroSemTituloOuComValorZeroResponde400() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId);
        String semTitulo = """
                {"turmaId":1,"disciplinaId":10,"tipo":"PROVA","data":"2026-10-10","valor":10}
                """;
        String valorZero = """
                {"turmaId":1,"disciplinaId":10,"tipo":"PROVA","titulo":"P1","data":"2026-10-10","valor":0}
                """;

        mockMvc.perform(com(json(post(URL), semTitulo), tokenProfessor(usuarioId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(json(post(URL), valorZero), tokenProfessor(usuarioId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notaNegativaResponde400() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId);
        String corpo = """
                {"notas":[{"alunoId":"%s","valor":-1}]}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(com(json(put(URL + "/" + UUID.randomUUID() + "/notas"), corpo), tokenProfessor(usuarioId)))
                .andExpect(status().isBadRequest());
        verify(notaRepository, never()).save(any());
    }

    @Test
    void excluirAvaliacaoComNotasResponde409() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor professor = professorLogado(usuarioId);
        Avaliacao avaliacao = Avaliacao.builder().id(UUID.randomUUID()).escolaId(ESCOLA)
                .turma(Turma.builder().id(1L).nome("1A").build())
                .disciplina(Disciplina.builder().id(10L).nome("Matematica").build()).professor(professor)
                .tipo(TipoAvaliacao.PROVA).titulo("Prova 1").data(LocalDate.of(2026, 10, 10))
                .valor(BigDecimal.TEN).build();
        when(avaliacaoRepository.findByIdAndEscolaId(avaliacao.getId(), ESCOLA)).thenReturn(Optional.of(avaliacao));
        when(alocacaoRepository.existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(ESCOLA, professor.getId(),
                1L, 10L)).thenReturn(true);
        when(notaRepository.existsByEscolaIdAndAvaliacaoId(ESCOLA, avaliacao.getId())).thenReturn(true);

        mockMvc.perform(com(delete(URL + "/" + avaliacao.getId()), tokenProfessor(usuarioId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Avaliacao ja tem notas lancadas e nao pode ser excluida"));
        verify(avaliacaoRepository, never()).delete(any());
    }
}
