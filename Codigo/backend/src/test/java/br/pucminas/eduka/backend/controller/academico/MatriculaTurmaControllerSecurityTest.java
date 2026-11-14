package br.pucminas.eduka.backend.controller.academico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.academico.Curso;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.academico.MatriculaTurmaService;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Contrato das matriculas, ponta a ponta: filter chain, tokens,
 * {@code @PreAuthorize}, validacao e servico reais; so os repositorios sao
 * mock.
 */
@WebMvcTest(MatriculaTurmaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        MatriculaTurmaService.class })
class MatriculaTurmaControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MatriculaTurmaRepository matriculaRepository;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private TurmaRepository turmaRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;

    private Pessoa pessoaAna;
    private Aluno ana;
    private Turma turma1A;
    private Turma turma1B;

    @BeforeEach
    void dados() {
        pessoaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        ana = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoaAna.getId())
                .matricula("2026001").build();
        // Em andamento: periodo planejado ou encerrado nao recebe matricula (IU09).
        PeriodoLetivo periodo = PeriodoLetivo.builder().id(5L).nome("2026").dataInicio(LocalDate.now().minusMonths(1))
                .dataFim(LocalDate.now().plusMonths(5)).build();
        Curso curso = Curso.builder().id(2L).nome("Fundamental II").build();
        turma1A = Turma.builder().id(1L).nome("6A").turno(Turno.MATUTINO).vagas(30).ativa(true)
                .periodoLetivo(periodo).curso(curso).build();
        turma1B = Turma.builder().id(2L).nome("6B").turno(Turno.VESPERTINO).vagas(30).ativa(true)
                .periodoLetivo(periodo).curso(curso).build();

        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(turmaRepository.findComLockByIdAndEscolaId(2L, ESCOLA_A)).thenReturn(Optional.of(turma1B));
        when(matriculaRepository.saveAndFlush(any(MatriculaTurma.class))).thenAnswer(inv -> {
            MatriculaTurma matricula = inv.getArgument(0);
            if (matricula.getId() == null) {
                matricula.setId(UUID.randomUUID());
            }
            return matricula;
        });
    }

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

    private String matricularAnaEm(long turmaId) {
        return "{\"alunoId\":\"" + ana.getId() + "\",\"turmaId\":" + turmaId + "}";
    }

    private MatriculaTurma matriculaDaAna(StatusMatricula status) {
        MatriculaTurma matricula = MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(ana)
                .turma(turma1A).status(status).dataMatricula(LocalDate.of(2026, 2, 1)).build();
        when(matriculaRepository.findByIdAndEscolaId(matricula.getId(), ESCOLA_A)).thenReturn(Optional.of(matricula));
        return matricula;
    }

    // ---------- Seguranca ----------

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/matriculas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soGestaoESecretariaMatriculam() throws Exception {
        for (String perfil : List.of("PROFESSOR", "FINANCEIRO", "ALUNO")) {
            String token = accessToken(perfil, ESCOLA_A);
            mockMvc.perform(json(post("/matriculas"), token, matricularAnaEm(1)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(patch("/matriculas/" + UUID.randomUUID()), token, "{\"status\":\"CANCELADA\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(json(post("/matriculas"), accessToken("SUPER_ADMIN", null), matricularAnaEm(1)))
                .andExpect(status().isForbidden());
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    @Test
    void financeiroConsultaMasProfessorEAlunoNao() throws Exception {
        mockMvc.perform(com(get("/matriculas"), accessToken("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk());
        for (String perfil : List.of("PROFESSOR", "ALUNO")) {
            String token = accessToken(perfil, ESCOLA_A);
            mockMvc.perform(com(get("/matriculas"), token)).andExpect(status().isForbidden());
            mockMvc.perform(com(get("/matriculas/pagina"), token)).andExpect(status().isForbidden());
            mockMvc.perform(com(get("/matriculas/" + UUID.randomUUID()), token)).andExpect(status().isForbidden());
        }
    }

    // ---------- Realizar matricula ----------

    @Test
    void secretariaMatriculaAlunoComoAtiva() throws Exception {
        mockMvc.perform(json(post("/matriculas"), accessToken("SECRETARIA", ESCOLA_A),
                "{\"alunoId\":\"" + ana.getId() + "\",\"turmaId\":1,\"dataMatricula\":\"2026-02-01\","
                        + "\"observacao\":\"  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.alunoNome").value("Ana"))
                .andExpect(jsonPath("$.alunoNumeroMatricula").value("2026001"))
                .andExpect(jsonPath("$.turmaNome").value("6A"))
                .andExpect(jsonPath("$.cursoNome").value("Fundamental II"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.dataMatricula").value("2026-02-01"))
                .andExpect(jsonPath("$.observacao").doesNotExist());

        ArgumentCaptor<MatriculaTurma> salva = ArgumentCaptor.forClass(MatriculaTurma.class);
        verify(matriculaRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
    }

    @Test
    void semDataUsaAtual() throws Exception {
        mockMvc.perform(json(post("/matriculas"), accessToken("MASTER", ESCOLA_A), matricularAnaEm(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataMatricula").isNotEmpty());
    }

    @Test
    void semAlunoOuTurmaResponde400() throws Exception {
        String token = accessToken("SECRETARIA", ESCOLA_A);
        mockMvc.perform(json(post("/matriculas"), token, "{\"turmaId\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/matriculas"), token, "{\"alunoId\":\"" + ana.getId() + "\"}"))
                .andExpect(status().isBadRequest());
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    @Test
    void alunoOuTurmaDeOutraEscolaResponde404() throws Exception {
        String token = accessToken("SECRETARIA", ESCOLA_A);
        mockMvc.perform(json(post("/matriculas"), token,
                "{\"alunoId\":\"" + UUID.randomUUID() + "\",\"turmaId\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aluno nao encontrado"));
        mockMvc.perform(json(post("/matriculas"), token, matricularAnaEm(99)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Turma nao encontrada"));
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    @Test
    void turmaInativaResponde400() throws Exception {
        turma1A.setAtiva(false);
        mockMvc.perform(json(post("/matriculas"), accessToken("SECRETARIA", ESCOLA_A), matricularAnaEm(1)))
                .andExpect(status().isBadRequest());
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    @Test
    void alunoJaMatriculadoNaTurmaResponde409() throws Exception {
        when(matriculaRepository.existsByEscolaIdAndAlunoIdAndTurmaId(ESCOLA_A, ana.getId(), 1L)).thenReturn(true);
        mockMvc.perform(json(post("/matriculas"), accessToken("SECRETARIA", ESCOLA_A), matricularAnaEm(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Aluno ja possui matricula nesta turma"));
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicadaBarradaPeloBancoResponde409() throws Exception {
        when(matriculaRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));
        mockMvc.perform(json(post("/matriculas"), accessToken("SECRETARIA", ESCOLA_A), matricularAnaEm(1)))
                .andExpect(status().isConflict());
    }

    @Test
    void turmaCheiaResponde409() throws Exception {
        when(matriculaRepository.countByEscolaIdAndTurmaIdAndStatus(ESCOLA_A, 1L, StatusMatricula.ATIVA))
                .thenReturn(30L);
        mockMvc.perform(json(post("/matriculas"), accessToken("SECRETARIA", ESCOLA_A), matricularAnaEm(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Turma sem vagas disponiveis"));
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    // ---------- Atualizar matricula ----------

    @Test
    void trancaEReativaAMatricula() throws Exception {
        MatriculaTurma matricula = matriculaDaAna(StatusMatricula.ATIVA);
        String token = accessToken("SECRETARIA", ESCOLA_A);

        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), token,
                "{\"status\":\"TRANCADA\",\"observacao\":\"Licenca medica\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANCADA"))
                .andExpect(jsonPath("$.observacao").value("Licenca medica"))
                .andExpect(jsonPath("$.atualizadoEm").isNotEmpty());

        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), token, "{\"status\":\"ATIVA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.observacao").value("Licenca medica"));
    }

    @Test
    void reativarEmTurmaCheiaResponde409() throws Exception {
        MatriculaTurma matricula = matriculaDaAna(StatusMatricula.TRANCADA);
        when(matriculaRepository.countByEscolaIdAndTurmaIdAndStatus(ESCOLA_A, 1L, StatusMatricula.ATIVA))
                .thenReturn(30L);

        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), accessToken("SECRETARIA", ESCOLA_A),
                "{\"status\":\"ATIVA\"}"))
                .andExpect(status().isConflict());
        assertThat(matricula.getStatus()).isEqualTo(StatusMatricula.TRANCADA);
    }

    @Test
    void matriculaCanceladaNaoMudaDeSituacao() throws Exception {
        MatriculaTurma matricula = matriculaDaAna(StatusMatricula.CANCELADA);

        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), accessToken("SECRETARIA", ESCOLA_A),
                "{\"status\":\"ATIVA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Matricula CANCELADA nao pode passar para ATIVA"));
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfereMatriculaAtivaDeTurma() throws Exception {
        MatriculaTurma matricula = matriculaDaAna(StatusMatricula.ATIVA);

        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), accessToken("MASTER", ESCOLA_A),
                "{\"turmaId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turmaId").value(2))
                .andExpect(jsonPath("$.turmaNome").value("6B"))
                .andExpect(jsonPath("$.status").value("ATIVA"));
    }

    @Test
    void naoTransfereMatriculaTrancada() throws Exception {
        MatriculaTurma matricula = matriculaDaAna(StatusMatricula.TRANCADA);

        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), accessToken("SECRETARIA", ESCOLA_A),
                "{\"turmaId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("So matricula ativa pode ser transferida de turma"));
        assertThat(matricula.getTurma()).isSameAs(turma1A);
    }

    @Test
    void transferirParaTurmaCheiaOuOndeJaEstaResponde409() throws Exception {
        MatriculaTurma matricula = matriculaDaAna(StatusMatricula.ATIVA);
        String token = accessToken("SECRETARIA", ESCOLA_A);

        when(matriculaRepository.existsByEscolaIdAndAlunoIdAndTurmaId(ESCOLA_A, ana.getId(), 2L)).thenReturn(true);
        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), token, "{\"turmaId\":2}"))
                .andExpect(status().isConflict());

        when(matriculaRepository.existsByEscolaIdAndAlunoIdAndTurmaId(ESCOLA_A, ana.getId(), 2L)).thenReturn(false);
        when(matriculaRepository.countByEscolaIdAndTurmaIdAndStatus(eq(ESCOLA_A), anyLong(), eq(StatusMatricula.ATIVA)))
                .thenReturn(30L);
        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), token, "{\"turmaId\":2}"))
                .andExpect(status().isConflict());

        assertThat(matricula.getTurma()).isSameAs(turma1A);
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    @Test
    void atualizacaoVaziaResponde400() throws Exception {
        MatriculaTurma matricula = matriculaDaAna(StatusMatricula.ATIVA);
        mockMvc.perform(json(patch("/matriculas/" + matricula.getId()), accessToken("SECRETARIA", ESCOLA_A), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matriculaDeOutraEscolaResponde404() throws Exception {
        mockMvc.perform(json(patch("/matriculas/" + UUID.randomUUID()), accessToken("SECRETARIA", ESCOLA_A),
                "{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Matricula nao encontrada"));
    }

    // ---------- Consulta ----------

    @Test
    void listaFiltrandoPorSituacao() throws Exception {
        MatriculaTurma ativa = matriculaDaAna(StatusMatricula.ATIVA);
        MatriculaTurma cancelada = MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(ana)
                .turma(turma1B).status(StatusMatricula.CANCELADA).dataMatricula(LocalDate.of(2026, 2, 1)).build();
        when(matriculaRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(ativa, cancelada));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));

        mockMvc.perform(com(get("/matriculas").param("status", "ATIVA"), accessToken("SECRETARIA", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ativa.getId().toString()))
                .andExpect(jsonPath("$[0].alunoNome").value("Ana"));
    }

    @Test
    void situacaoInexistenteNoFiltroResponde400() throws Exception {
        mockMvc.perform(com(get("/matriculas").param("status", "XPTO"), accessToken("SECRETARIA", ESCOLA_A)))
                .andExpect(status().isBadRequest());
    }
}
