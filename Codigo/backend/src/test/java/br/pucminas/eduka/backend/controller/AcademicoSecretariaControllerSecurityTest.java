package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.CursoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.AcademicoSecretariaService;

/**
 * Contrato de seguranca da estrutura academica, ponta a ponta: filter chain,
 * tokens, {@code @PreAuthorize}, validacao e servico reais; so os
 * repositorios sao mock.
 */
@WebMvcTest(AcademicoSecretariaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        AcademicoSecretariaService.class })
class AcademicoSecretariaControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String BASE = "/academico/secretaria";
    private static final String CURSO_VALIDO = "{\"nome\":\"Ensino Medio\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PeriodoLetivoRepository periodoLetivoRepository;
    @MockitoBean
    private CursoRepository cursoRepository;
    @MockitoBean
    private DisciplinaRepository disciplinaRepository;
    @MockitoBean
    private TurmaRepository turmaRepository;
    @MockitoBean
    private EscolaRepository escolaRepository;

    private String accessToken(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token,
            String corpo) {
        return com(request, token).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(BASE + "/cursos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alunoESuperAdminNaoEntramNoModulo() throws Exception {
        for (String token : List.of(accessToken("ALUNO", ESCOLA_A), accessToken("SUPER_ADMIN", null))) {
            mockMvc.perform(com(get(BASE + "/turmas"), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(post(BASE + "/cursos"), token, CURSO_VALIDO))
                    .andExpect(status().isForbidden());
        }
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void professorEFinanceiroLeemMasNaoCadastram() throws Exception {
        for (String perfil : List.of("PROFESSOR", "FINANCEIRO")) {
            String token = accessToken(perfil, ESCOLA_A);
            mockMvc.perform(com(get(BASE + "/cursos"), token))
                    .andExpect(status().isOk());
            mockMvc.perform(json(post(BASE + "/cursos"), token, CURSO_VALIDO))
                    .andExpect(status().isForbidden());
        }
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void secretariaCadastraCursoNaEscolaDoToken() throws Exception {
        when(cursoRepository.save(any(Curso.class))).thenAnswer(inv -> {
            Curso curso = inv.getArgument(0);
            curso.setId(7L);
            return curso;
        });

        mockMvc.perform(json(post(BASE + "/cursos"), accessToken("SECRETARIA", ESCOLA_A),
                "{\"nome\":\" Ensino Medio \",\"codigo\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE + "/cursos/7"))
                .andExpect(jsonPath("$.nome").value("Ensino Medio"))
                .andExpect(jsonPath("$.codigo").doesNotExist())
                .andExpect(jsonPath("$.escolaId").value(ESCOLA_A.toString()));
        verify(escolaRepository).getReferenceById(ESCOLA_A);
    }

    @Test
    void periodoComFimAntesDoInicioResponde400() throws Exception {
        mockMvc.perform(json(post(BASE + "/periodos-letivos"), accessToken("MASTER", ESCOLA_A),
                "{\"nome\":\"2026\",\"dataInicio\":\"2026-12-01\",\"dataFim\":\"2026-02-01\"}"))
                .andExpect(status().isBadRequest());
        verify(periodoLetivoRepository, never()).save(any());
    }

    @Test
    void disciplinaSemCargaHorariaResponde400() throws Exception {
        mockMvc.perform(json(post(BASE + "/disciplinas"), accessToken("SECRETARIA", ESCOLA_A),
                "{\"nome\":\"Matematica\"}"))
                .andExpect(status().isBadRequest());
        verify(disciplinaRepository, never()).save(any());
    }

    @Test
    void turmaComCursoDeOutraEscolaResponde404() throws Exception {
        when(periodoLetivoRepository.findByIdAndEscolaId(1L, ESCOLA_A))
                .thenReturn(Optional.of(PeriodoLetivo.builder().id(1L).nome("2026").build()));
        when(cursoRepository.findByIdAndEscolaId(2L, ESCOLA_A)).thenReturn(Optional.empty());

        mockMvc.perform(json(post(BASE + "/turmas"), accessToken("SECRETARIA", ESCOLA_A),
                "{\"periodoLetivoId\":1,\"cursoId\":2,\"nome\":\"1A\",\"vagas\":30,\"turno\":\"MATUTINO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Curso nao encontrado"));
        verify(turmaRepository, never()).save(any());
    }

    @Test
    void turmaComDisciplinaDeOutraEscolaResponde404() throws Exception {
        when(periodoLetivoRepository.findByIdAndEscolaId(1L, ESCOLA_A))
                .thenReturn(Optional.of(PeriodoLetivo.builder().id(1L).nome("2026").build()));
        when(cursoRepository.findByIdAndEscolaId(2L, ESCOLA_A))
                .thenReturn(Optional.of(Curso.builder().id(2L).nome("Fundamental").build()));
        when(disciplinaRepository.findAllByIdInAndEscolaId(any(), any()))
                .thenReturn(List.of(Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build()));

        mockMvc.perform(json(post(BASE + "/turmas"), accessToken("SECRETARIA", ESCOLA_A),
                "{\"periodoLetivoId\":1,\"cursoId\":2,\"nome\":\"1A\",\"vagas\":30,\"turno\":\"MATUTINO\","
                        + "\"disciplinaIds\":[10,99]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Disciplina nao encontrada"));
        verify(turmaRepository, never()).save(any());
    }

    @Test
    void secretariaCadastraTurmaComGrade() throws Exception {
        when(periodoLetivoRepository.findByIdAndEscolaId(1L, ESCOLA_A))
                .thenReturn(Optional.of(PeriodoLetivo.builder().id(1L).nome("2026").build()));
        when(cursoRepository.findByIdAndEscolaId(2L, ESCOLA_A))
                .thenReturn(Optional.of(Curso.builder().id(2L).nome("Fundamental").build()));
        when(disciplinaRepository.findAllByIdInAndEscolaId(List.of(10L), ESCOLA_A))
                .thenReturn(List.of(Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build()));
        when(turmaRepository.save(any(Turma.class))).thenAnswer(inv -> {
            Turma turma = inv.getArgument(0);
            turma.setId(3L);
            return turma;
        });

        mockMvc.perform(json(post(BASE + "/turmas"), accessToken("SECRETARIA", ESCOLA_A),
                "{\"periodoLetivoId\":1,\"cursoId\":2,\"nome\":\"1A\",\"vagas\":30,\"turno\":\"MATUTINO\","
                        + "\"disciplinaIds\":[10]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodoLetivoNome").value("2026"))
                .andExpect(jsonPath("$.cursoNome").value("Fundamental"))
                .andExpect(jsonPath("$.disciplinas[0].nome").value("Matematica"));
    }
}
