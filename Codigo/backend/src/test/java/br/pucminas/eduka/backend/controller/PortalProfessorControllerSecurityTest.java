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

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.domain.Turno;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.PortalProfessorService;

/**
 * Contrato de seguranca de {@code /portal/professor}, ponta a ponta: filter
 * chain, tokens, {@code @PreAuthorize} e servico reais; so os repositorios
 * sao mock.
 */
@WebMvcTest(PortalProfessorController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        PortalProfessorService.class })
class PortalProfessorControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String URL = "/portal/professor/turmas";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ProfessorRepository professorRepository;
    @MockitoBean
    private AlocacaoProfessorRepository alocacaoRepository;

    private String accessToken(UUID usuarioId, String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(usuarioId, perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static Turma turma(Long id, String nome) {
        return Turma.builder().id(id).nome(nome).turno(Turno.MATUTINO).vagas(30)
                .periodoLetivo(PeriodoLetivo.builder().id(5L).nome("2026").build())
                .curso(Curso.builder().id(7L).nome("Ensino Medio").build())
                .build();
    }

    private static AlocacaoProfessor alocacao(Professor professor, Turma turma, Disciplina disciplina) {
        return AlocacaoProfessor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).professor(professor)
                .turma(turma).disciplina(disciplina).build();
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soProfessorAcessa() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "ALUNO")) {
            mockMvc.perform(com(get(URL), accessToken(UUID.randomUUID(), perfil, ESCOLA_A)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(com(get(URL), accessToken(UUID.randomUUID(), "SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
        verify(professorRepository, never()).findByUsuarioIdAndEscolaId(any(), any());
    }

    @Test
    void listaSoAsTurmasEDisciplinasDoProfessorDoToken() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor ana = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).usuarioId(usuarioId)
                .ativo(true).build();
        Turma turmaB = turma(2L, "2B");
        Turma turmaA = turma(1L, "1A");
        Disciplina matematica = Disciplina.builder().id(10L).nome("Matematica").codigo("MAT").cargaHoraria(80)
                .build();
        Disciplina fisica = Disciplina.builder().id(11L).nome("Fisica").cargaHoraria(60).build();
        when(professorRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(ana));
        when(alocacaoRepository.findAllComTurmaEDisciplinaByEscolaIdAndProfessorId(ESCOLA_A, ana.getId()))
                .thenReturn(List.of(alocacao(ana, turmaB, matematica), alocacao(ana, turmaA, matematica),
                        alocacao(ana, turmaA, fisica)));

        mockMvc.perform(com(get(URL), accessToken(usuarioId, "PROFESSOR", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("1A"))
                .andExpect(jsonPath("$[0].periodoLetivoNome").value("2026"))
                .andExpect(jsonPath("$[0].cursoNome").value("Ensino Medio"))
                .andExpect(jsonPath("$[0].disciplinas.length()").value(2))
                .andExpect(jsonPath("$[0].disciplinas[0].nome").value("Fisica"))
                .andExpect(jsonPath("$[0].disciplinas[1].nome").value("Matematica"))
                .andExpect(jsonPath("$[0].disciplinas[1].cargaHoraria").value(80))
                .andExpect(jsonPath("$[1].nome").value("2B"))
                .andExpect(jsonPath("$[1].disciplinas.length()").value(1));

        verify(alocacaoRepository, never()).findAll();
    }

    @Test
    void professorSemAlocacaoRecebeListaVazia() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor ana = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).usuarioId(usuarioId)
                .ativo(true).build();
        when(professorRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(ana));

        mockMvc.perform(com(get(URL), accessToken(usuarioId, "PROFESSOR", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void loginSemCadastroDeProfessorResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(professorRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.empty());

        mockMvc.perform(com(get(URL), accessToken(usuarioId, "PROFESSOR", ESCOLA_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Professor nao encontrado"));
        verify(alocacaoRepository, never()).findAllComTurmaEDisciplinaByEscolaIdAndProfessorId(any(), any());
    }
}
