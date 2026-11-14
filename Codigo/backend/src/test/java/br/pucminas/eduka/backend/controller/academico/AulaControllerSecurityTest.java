package br.pucminas.eduka.backend.controller.academico;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.academico.Aula;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.Frequencia;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.AulaRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.academico.AulaService;

/**
 * Contrato de seguranca e de erros de {@code /portal/professor/aulas}, ponta
 * a ponta: filter chain, tokens, {@code @PreAuthorize}, validacao e servico
 * reais; so os repositorios sao mock.
 */
@WebMvcTest(AulaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class, AulaService.class })
class AulaControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final String URL = "/portal/professor/aulas";
    private static final UUID ALUNO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AulaRepository aulaRepository;
    @MockitoBean
    private FrequenciaRepository frequenciaRepository;
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

    private static String registro(String data, String conteudo, String chamada) {
        return """
                {"turmaId":1,"disciplinaId":10,"data":"%s","conteudo":%s,"chamada":%s}
                """.formatted(data, conteudo, chamada);
    }

    private static String chamada(boolean presente) {
        return "[{\"alunoId\":\"%s\",\"presente\":%s}]".formatted(ALUNO, presente);
    }

    private static String ontem() {
        return LocalDate.now().minusDays(1).toString();
    }

    private Professor professorLogado(UUID usuarioId, boolean alocado) {
        Professor professor = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA).usuarioId(usuarioId)
                .ativo(true).build();
        when(professorRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA)).thenReturn(Optional.of(professor));
        when(alocacaoRepository.existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(ESCOLA, professor.getId(),
                1L, 10L)).thenReturn(alocado);
        return professor;
    }

    private String tokenProfessor(UUID usuarioId) {
        return jwtService.generateAccessToken(usuarioId, "PROFESSOR", ESCOLA);
    }

    /** Turma com um unico aluno ativo, o {@link #ALUNO}. */
    private Aluno turmaComUmAluno() {
        Aluno aluno = Aluno.builder().id(ALUNO).escolaId(ESCOLA).pessoaId(UUID.randomUUID()).matricula("2026-0001")
                .build();
        Turma turma = Turma.builder().id(1L).nome("1A").build();
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 1L)).thenReturn(List.of(
                MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA).aluno(aluno).turma(turma)
                        .status(StatusMatricula.ATIVA).dataMatricula(LocalDate.now().minusDays(30)).build()));
        when(pessoaRepository.findAllByIdInAndEscolaId(anyList(), any())).thenReturn(
                List.of(Pessoa.builder().id(aluno.getPessoaId()).escolaId(ESCOLA).nome("Ana").ativo(true).build()));
        return aluno;
    }

    private Aula aula(Professor professor) {
        return Aula.builder().id(UUID.randomUUID()).escolaId(ESCOLA)
                .turma(Turma.builder().id(1L).nome("1A").build())
                .disciplina(Disciplina.builder().id(10L).nome("Matematica").build()).professor(professor)
                .data(LocalDate.now().minusDays(1)).conteudo("Fracoes").build();
    }

    // ---- 401 e 403 ----

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(URL).param("turmaId", "1").param("disciplinaId", "10"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post(URL), registro(ontem(), "\"Fracoes\"", chamada(true))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(put(URL + "/" + UUID.randomUUID()), "{\"conteudo\":\"x\",\"chamada\":[]}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void soProfessorAcessa() throws Exception {
        UUID id = UUID.randomUUID();
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "ALUNO")) {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA);
            mockMvc.perform(com(get(URL).param("turmaId", "1").param("disciplinaId", "10"), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(get(URL + "/" + id), token)).andExpect(status().isForbidden());
            mockMvc.perform(com(json(post(URL), registro(ontem(), "\"Fracoes\"", chamada(true))), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(json(put(URL + "/" + id), "{\"conteudo\":\"x\",\"chamada\":[]}"), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(delete(URL + "/" + id), token)).andExpect(status().isForbidden());
        }
        String superAdmin = jwtService.generateAccessToken(UUID.randomUUID(), "SUPER_ADMIN", null);
        mockMvc.perform(com(get(URL).param("turmaId", "1").param("disciplinaId", "10"), superAdmin))
                .andExpect(status().isForbidden());
        mockMvc.perform(com(delete(URL + "/" + id), superAdmin)).andExpect(status().isForbidden());
        verify(aulaRepository, never()).saveAndFlush(any());
        verify(aulaRepository, never()).delete(any());
        verify(frequenciaRepository, never()).apagarDaAula(any(), any());
        verify(professorRepository, never()).findByUsuarioIdAndEscolaId(any(), any());
    }

    // ---- registrar ----

    @Test
    void professorRegistraAulaComChamadaEResponde201() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, true);
        Aluno aluno = turmaComUmAluno();
        when(turmaRepository.getReferenceById(1L)).thenReturn(Turma.builder().id(1L).nome("1A").build());
        when(disciplinaRepository.getReferenceById(10L))
                .thenReturn(Disciplina.builder().id(10L).nome("Matematica").build());
        when(aulaRepository.saveAndFlush(any(Aula.class))).thenAnswer(inv -> {
            Aula a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            when(aulaRepository.findByIdAndEscolaId(a.getId(), ESCOLA)).thenReturn(Optional.of(a));
            when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, a.getId())).thenReturn(List.of(
                    Frequencia.builder().aula(a).escolaId(ESCOLA).presente(false).aluno(aluno).build()));
            return a;
        });

        mockMvc.perform(com(json(post(URL), registro(ontem(), "\"  Fracoes  \"", chamada(false)))
                , tokenProfessor(usuarioId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conteudo").value("Fracoes"))
                .andExpect(jsonPath("$.turmaNome").value("1A"))
                .andExpect(jsonPath("$.faltas").value(1))
                .andExpect(jsonPath("$.presentes").value(0))
                .andExpect(jsonPath("$.chamada[0].alunoNome").value("Ana"))
                .andExpect(jsonPath("$.chamada[0].presente").value(false));
        verify(frequenciaRepository).saveAll(anyList());
    }

    @Test
    void registroEmDisciplinaNaoAlocadaResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, false);

        mockMvc.perform(com(json(post(URL), registro(ontem(), "\"Fracoes\"", chamada(true))),
                tokenProfessor(usuarioId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Turma ou disciplina nao alocada ao professor"));
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroComDataFuturaResponde400() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, true);

        mockMvc.perform(com(json(post(URL), registro(LocalDate.now().plusDays(2).toString(), "\"Fracoes\"",
                chamada(true))), tokenProfessor(usuarioId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A data da aula nao pode ser futura"));
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroComChamadaIncompletaResponde400() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, true);
        turmaComUmAluno();

        mockMvc.perform(com(json(post(URL), registro(ontem(), "\"Fracoes\"", "[]")), tokenProfessor(usuarioId)))
                .andExpect(status().isBadRequest());
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroComAlunoForaDaTurmaResponde400() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, true);
        turmaComUmAluno();
        String comIntruso = "[{\"alunoId\":\"%s\",\"presente\":true},{\"alunoId\":\"%s\",\"presente\":true}]"
                .formatted(ALUNO, UUID.randomUUID());

        mockMvc.perform(com(json(post(URL), registro(ontem(), "\"Fracoes\"", comIntruso)), tokenProfessor(usuarioId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Aluno sem matricula ativa na turma da aula"));
    }

    @Test
    void registroSemConteudoOuSemChamadaOuSemPresenteResponde400() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, true);
        String token = tokenProfessor(usuarioId);

        mockMvc.perform(com(json(post(URL), registro(ontem(), "\"  \"", chamada(true))), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(json(post(URL), registro(ontem(), "\"x\"", "null")), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(json(post(URL), registro(ontem(), "\"x\"", "[{\"alunoId\":\"%s\"}]".formatted(ALUNO))),
                token)).andExpect(status().isBadRequest());
        mockMvc.perform(com(json(post(URL), registro(ontem(), "\"" + "a".repeat(2001) + "\"", chamada(true))), token))
                .andExpect(status().isBadRequest());
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    // ---- listar e buscar ----

    @Test
    void listaAsAulasEmPaginasEBarraPaginaInvalida() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor professor = professorLogado(usuarioId, true);
        Aula aula = aula(professor);
        when(aulaRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaId(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(aula), PageRequest.of(0, 10), 11));
        when(frequenciaRepository.contarPorAula(any(), any())).thenReturn(List.of());

        mockMvc.perform(com(get(URL).param("turmaId", "1").param("disciplinaId", "10").param("tamanho", "10"),
                tokenProfessor(usuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(11))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.conteudo[0].conteudo").value("Fracoes"))
                .andExpect(jsonPath("$.conteudo[0].disciplinaNome").value("Matematica"));
        mockMvc.perform(com(get(URL).param("turmaId", "1").param("disciplinaId", "10").param("tamanho", "101"),
                tokenProfessor(usuarioId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listagemDeDisciplinaNaoAlocadaResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, false);

        mockMvc.perform(com(get(URL).param("turmaId", "1").param("disciplinaId", "10"), tokenProfessor(usuarioId)))
                .andExpect(status().isNotFound());
        verify(aulaRepository, never()).findAllByEscolaIdAndTurmaIdAndDisciplinaId(any(), any(), any(), any());
    }

    @Test
    void aulaInexistenteOuDeOutraEscolaResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, true);
        UUID id = UUID.randomUUID();
        when(aulaRepository.findByIdAndEscolaId(id, ESCOLA)).thenReturn(Optional.empty());

        mockMvc.perform(com(get(URL + "/" + id), tokenProfessor(usuarioId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aula nao encontrada"));
        mockMvc.perform(com(json(put(URL + "/" + id), "{\"conteudo\":\"x\",\"chamada\":[]}"),
                tokenProfessor(usuarioId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aulaDeOutraAlocacaoResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor professor = professorLogado(usuarioId, false);
        Aula aula = aula(professor);
        when(aulaRepository.findByIdAndEscolaId(aula.getId(), ESCOLA)).thenReturn(Optional.of(aula));

        mockMvc.perform(com(get(URL + "/" + aula.getId()), tokenProfessor(usuarioId)))
                .andExpect(status().isNotFound());
        verify(frequenciaRepository, never()).findAllByEscolaIdAndAulaId(any(), any());
    }

    // ---- excluir ----

    @Test
    void professorExcluiASuaAulaEResponde204() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor professor = professorLogado(usuarioId, true);
        Aula aula = aula(professor);
        when(aulaRepository.findByIdAndEscolaId(aula.getId(), ESCOLA)).thenReturn(Optional.of(aula));

        mockMvc.perform(com(delete(URL + "/" + aula.getId()), tokenProfessor(usuarioId)))
                .andExpect(status().isNoContent());
        verify(frequenciaRepository).apagarDaAula(ESCOLA, aula.getId());
        verify(aulaRepository).delete(aula);
    }

    @Test
    void excluirAulaInexistenteOuDeOutraAlocacaoResponde404SemApagar() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor professor = professorLogado(usuarioId, false);
        Aula deOutraAlocacao = aula(professor);
        when(aulaRepository.findByIdAndEscolaId(deOutraAlocacao.getId(), ESCOLA))
                .thenReturn(Optional.of(deOutraAlocacao));
        UUID inexistente = UUID.randomUUID();
        when(aulaRepository.findByIdAndEscolaId(inexistente, ESCOLA)).thenReturn(Optional.empty());

        mockMvc.perform(com(delete(URL + "/" + deOutraAlocacao.getId()), tokenProfessor(usuarioId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(com(delete(URL + "/" + inexistente), tokenProfessor(usuarioId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aula nao encontrada"));
        verify(frequenciaRepository, never()).apagarDaAula(any(), any());
        verify(aulaRepository, never()).delete(any());
    }

    // ---- corrigir ----

    @Test
    void professorCorrigeConteudoEChamada() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor professor = professorLogado(usuarioId, true);
        Aluno aluno = turmaComUmAluno();
        Aula aula = aula(professor);
        Frequencia registrada = Frequencia.builder().id(UUID.randomUUID()).aula(aula).escolaId(ESCOLA).aluno(aluno)
                .presente(false).build();
        when(aulaRepository.findByIdAndEscolaId(aula.getId(), ESCOLA)).thenReturn(Optional.of(aula));
        when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, aula.getId())).thenReturn(List.of(registrada));

        mockMvc.perform(com(json(put(URL + "/" + aula.getId()),
                "{\"conteudo\":\"Decimais\",\"chamada\":%s}".formatted(chamada(true))), tokenProfessor(usuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").value("Decimais"))
                .andExpect(jsonPath("$.presentes").value(1))
                .andExpect(jsonPath("$.faltas").value(0))
                .andExpect(jsonPath("$.chamada[0].presente").value(true))
                .andExpect(jsonPath("$.atualizadoEm").isNotEmpty());
    }

    @Test
    void correcaoComChamadaIncompletaResponde400() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Professor professor = professorLogado(usuarioId, true);
        turmaComUmAluno();
        Aula aula = aula(professor);
        when(aulaRepository.findByIdAndEscolaId(aula.getId(), ESCOLA)).thenReturn(Optional.of(aula));
        when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, aula.getId())).thenReturn(List.of());

        mockMvc.perform(com(json(put(URL + "/" + aula.getId()), "{\"conteudo\":\"Decimais\",\"chamada\":[]}"),
                tokenProfessor(usuarioId)))
                .andExpect(status().isBadRequest());
        verify(aulaRepository, never()).save(any());
    }

    // ---- chamada do dia ----

    @Test
    void chamadaDoDiaSoParaOProfessor() throws Exception {
        mockMvc.perform(get(URL + "/chamada").param("turmaId", "1").param("disciplinaId", "10"))
                .andExpect(status().isUnauthorized());
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "ALUNO")) {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA);
            mockMvc.perform(com(get(URL + "/chamada").param("turmaId", "1").param("disciplinaId", "10"), token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void chamadaDoDiaTrazOsAlunosEBarraDataFuturaOuInvalidaEDisciplinaNaoAlocada() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        professorLogado(usuarioId, true);
        turmaComUmAluno();
        String token = tokenProfessor(usuarioId);

        mockMvc.perform(com(get(URL + "/chamada").param("turmaId", "1").param("disciplinaId", "10")
                .param("data", ontem()), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(ontem()))
                .andExpect(jsonPath("$.alunos[0].alunoNome").value("Ana"))
                .andExpect(jsonPath("$.alunos[0].alunoId").value(ALUNO.toString()))
                .andExpect(jsonPath("$.aulas").isEmpty());
        // A rota fixa nao pode cair em /{id}, que so aceita UUID.
        mockMvc.perform(com(get(URL + "/chamada").param("turmaId", "1").param("disciplinaId", "10")
                .param("data", LocalDate.now().plusDays(2).toString()), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(get(URL + "/chamada").param("turmaId", "1").param("disciplinaId", "10")
                .param("data", "ontem"), token))
                .andExpect(status().isBadRequest());

        UUID outroUsuario = UUID.randomUUID();
        professorLogado(outroUsuario, false);
        mockMvc.perform(com(get(URL + "/chamada").param("turmaId", "1").param("disciplinaId", "10"),
                tokenProfessor(outroUsuario)))
                .andExpect(status().isNotFound());
    }
}
