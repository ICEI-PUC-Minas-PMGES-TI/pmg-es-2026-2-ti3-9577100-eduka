package br.pucminas.eduka.backend.controller.academico;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.academico.AlocacaoProfessorService;

/**
 * Contrato de seguranca das alocacoes de professor, ponta a ponta: filter
 * chain, tokens, {@code @PreAuthorize}, validacao e servico reais; so os
 * repositorios sao mock.
 */
@WebMvcTest(AlocacaoProfessorController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        AlocacaoProfessorService.class })
class AlocacaoProfessorControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String CORPO_VALIDO = "{\"turmaId\":1,\"disciplinaId\":10}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AlocacaoProfessorRepository alocacaoRepository;
    @MockitoBean
    private ProfessorRepository professorRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private TurmaRepository turmaRepository;
    @MockitoBean
    private DisciplinaRepository disciplinaRepository;

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
        mockMvc.perform(get("/professores/" + UUID.randomUUID() + "/alocacoes"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/turmas/1/alocacoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soGestaoESecretariaAcessam() throws Exception {
        String url = "/professores/" + UUID.randomUUID() + "/alocacoes";
        for (String perfil : List.of("PROFESSOR", "FINANCEIRO", "ALUNO")) {
            String token = accessToken(perfil, ESCOLA_A);
            mockMvc.perform(com(get(url), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(post(url), token, CORPO_VALIDO))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(get("/turmas/1/alocacoes"), token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(com(delete(url + "/" + UUID.randomUUID()), accessToken("SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
        verify(alocacaoRepository, never()).saveAndFlush(any());
        verify(alocacaoRepository, never()).delete(any());
    }

    @Test
    void semTurmaOuDisciplinaResponde400() throws Exception {
        String url = "/professores/" + UUID.randomUUID() + "/alocacoes";
        String token = accessToken("SECRETARIA", ESCOLA_A);
        mockMvc.perform(json(post(url), token, "{\"disciplinaId\":10}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(url), token, "{\"turmaId\":1}"))
                .andExpect(status().isBadRequest());
        verify(alocacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void professorDeOutraEscolaResponde404() throws Exception {
        UUID professorId = UUID.randomUUID();
        when(professorRepository.findByIdAndEscolaId(professorId, ESCOLA_A)).thenReturn(Optional.empty());

        mockMvc.perform(json(post("/professores/" + professorId + "/alocacoes"),
                accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Professor nao encontrado"));
        verify(alocacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void secretariaAlocaProfessorNaDisciplinaDaTurma() throws Exception {
        Pessoa pessoaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        Professor ana = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoaAna.getId())
                .ativo(true).build();
        Disciplina matematica = Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build();
        Turma turma = Turma.builder().id(1L).nome("1A").turno(Turno.MATUTINO).vagas(30)
                .periodoLetivo(PeriodoLetivo.builder().id(5L).nome("2026").build())
                .disciplinas(Set.of(matematica)).build();
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma));
        when(disciplinaRepository.findByIdAndEscolaId(10L, ESCOLA_A)).thenReturn(Optional.of(matematica));
        when(alocacaoRepository.saveAndFlush(any(AlocacaoProfessor.class))).thenAnswer(inv -> {
            AlocacaoProfessor alocacao = inv.getArgument(0);
            alocacao.setId(UUID.randomUUID());
            return alocacao;
        });

        mockMvc.perform(json(post("/professores/" + ana.getId() + "/alocacoes"),
                accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.professorNome").value("Ana"))
                .andExpect(jsonPath("$.turmaNome").value("1A"))
                .andExpect(jsonPath("$.disciplinaNome").value("Matematica"));
    }

    @Test
    void disciplinaJaAlocadaNaTurmaResponde409() throws Exception {
        Professor ana = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(UUID.randomUUID())
                .ativo(true).build();
        Disciplina matematica = Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build();
        Turma turma = Turma.builder().id(1L).nome("1A").disciplinas(Set.of(matematica)).build();
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma));
        when(disciplinaRepository.findByIdAndEscolaId(10L, ESCOLA_A)).thenReturn(Optional.of(matematica));
        when(alocacaoRepository.existsByEscolaIdAndTurmaIdAndDisciplinaId(ESCOLA_A, 1L, 10L)).thenReturn(true);

        mockMvc.perform(json(post("/professores/" + ana.getId() + "/alocacoes"),
                accessToken("MASTER", ESCOLA_A), CORPO_VALIDO))
                .andExpect(status().isConflict());
        verify(alocacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void desalocarResponde204() throws Exception {
        UUID professorId = UUID.randomUUID();
        AlocacaoProfessor alocacao = AlocacaoProfessor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).build();
        when(alocacaoRepository.findByIdAndEscolaIdAndProfessorId(alocacao.getId(), ESCOLA_A, professorId))
                .thenReturn(Optional.of(alocacao));

        mockMvc.perform(com(delete("/professores/" + professorId + "/alocacoes/" + alocacao.getId()),
                accessToken("SECRETARIA", ESCOLA_A)))
                .andExpect(status().isNoContent());
        verify(alocacaoRepository).delete(alocacao);
    }
}
