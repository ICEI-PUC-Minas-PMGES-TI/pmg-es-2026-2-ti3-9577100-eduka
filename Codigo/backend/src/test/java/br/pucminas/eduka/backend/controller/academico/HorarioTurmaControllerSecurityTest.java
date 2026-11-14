package br.pucminas.eduka.backend.controller.academico;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
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

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.HorarioTurma;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.HorarioTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.academico.HorarioTurmaService;

/**
 * Contrato de seguranca da grade de horarios, ponta a ponta: filter chain,
 * tokens, {@code @PreAuthorize}, validacao e servico reais; so os
 * repositorios sao mock.
 */
@WebMvcTest(HorarioTurmaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        HorarioTurmaService.class })
class HorarioTurmaControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ALOCACAO_ID = UUID.randomUUID();
    private static final String URL = "/turmas/1/horario";
    private static final String CORPO_VALIDO = "{\"alocacaoId\":\"" + ALOCACAO_ID
            + "\",\"diaSemana\":\"SEGUNDA\",\"horaInicio\":\"08:00\",\"horaFim\":\"09:00\"}";
    private static final String CORPO_AJUSTE = "{\"diaSemana\":\"TERCA\",\"horaInicio\":\"10:00\",\"horaFim\":\"11:00\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private HorarioTurmaRepository horarioRepository;
    @MockitoBean
    private AlocacaoProfessorRepository alocacaoRepository;
    @MockitoBean
    private TurmaRepository turmaRepository;
    @MockitoBean
    private ProfessorRepository professorRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;

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

    /** Turma 1, professor Ana e a alocacao dela, prontos para a criacao. */
    private HorarioTurma prepararCriacao() {
        Pessoa pessoaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        Professor ana = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoaAna.getId())
                .ativo(true).build();
        Disciplina matematica = Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build();
        Turma turma = Turma.builder().id(1L).nome("1A").build();
        AlocacaoProfessor alocacao = AlocacaoProfessor.builder().id(ALOCACAO_ID).escolaId(ESCOLA_A).professor(ana)
                .turma(turma).disciplina(matematica).build();
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma));
        when(alocacaoRepository.findByIdAndEscolaId(ALOCACAO_ID, ESCOLA_A)).thenReturn(Optional.of(alocacao));
        when(professorRepository.findComLockByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(pessoaAna.getId()), ESCOLA_A))
                .thenReturn(List.of(pessoaAna));
        HorarioTurma existente = HorarioTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).alocacao(alocacao)
                .diaSemana(DiaSemana.SEGUNDA).horaInicio(LocalTime.of(8, 0)).horaFim(LocalTime.of(9, 0)).build();
        when(horarioRepository.saveAndFlush(any(HorarioTurma.class))).thenAnswer(inv -> {
            HorarioTurma aula = inv.getArgument(0);
            aula.setId(UUID.randomUUID());
            return aula;
        });
        return existente;
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soGestaoESecretariaAcessam() throws Exception {
        String aula = URL + "/" + UUID.randomUUID();
        for (String perfil : List.of("PROFESSOR", "FINANCEIRO", "ALUNO")) {
            String token = accessToken(perfil, ESCOLA_A);
            mockMvc.perform(com(get(URL), token)).andExpect(status().isForbidden());
            mockMvc.perform(json(post(URL), token, CORPO_VALIDO)).andExpect(status().isForbidden());
            mockMvc.perform(json(put(aula), token, CORPO_AJUSTE)).andExpect(status().isForbidden());
            mockMvc.perform(com(delete(aula), token)).andExpect(status().isForbidden());
        }
        mockMvc.perform(com(get(URL), accessToken("SUPER_ADMIN", null))).andExpect(status().isForbidden());
        verify(horarioRepository, never()).saveAndFlush(any());
        verify(horarioRepository, never()).delete(any());
    }

    @Test
    void camposObrigatoriosAusentesResponde400() throws Exception {
        String token = accessToken("SECRETARIA", ESCOLA_A);
        mockMvc.perform(json(post(URL), token,
                "{\"diaSemana\":\"SEGUNDA\",\"horaInicio\":\"08:00\",\"horaFim\":\"09:00\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(URL), token,
                "{\"alocacaoId\":\"" + ALOCACAO_ID + "\",\"horaInicio\":\"08:00\",\"horaFim\":\"09:00\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(URL), token,
                "{\"alocacaoId\":\"" + ALOCACAO_ID + "\",\"diaSemana\":\"SEGUNDA\",\"horaFim\":\"09:00\"}"))
                .andExpect(status().isBadRequest());
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void domingoOuHoraMalformadaResponde400() throws Exception {
        String token = accessToken("SECRETARIA", ESCOLA_A);
        mockMvc.perform(json(post(URL), token, "{\"alocacaoId\":\"" + ALOCACAO_ID
                + "\",\"diaSemana\":\"DOMINGO\",\"horaInicio\":\"08:00\",\"horaFim\":\"09:00\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(URL), token, "{\"alocacaoId\":\"" + ALOCACAO_ID
                + "\",\"diaSemana\":\"SEGUNDA\",\"horaInicio\":\"oito\",\"horaFim\":\"09:00\"}"))
                .andExpect(status().isBadRequest());
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void secretariaMontaAulaDaGrade() throws Exception {
        prepararCriacao();

        mockMvc.perform(json(post(URL), accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.diaSemana").value("SEGUNDA"))
                .andExpect(jsonPath("$.horaInicio").value("08:00:00"))
                .andExpect(jsonPath("$.horaFim").value("09:00:00"))
                .andExpect(jsonPath("$.disciplinaNome").value("Matematica"))
                .andExpect(jsonPath("$.professorNome").value("Ana"));
    }

    @Test
    void fimAntesDoInicioResponde400() throws Exception {
        prepararCriacao();

        mockMvc.perform(json(post(URL), accessToken("MASTER", ESCOLA_A), "{\"alocacaoId\":\"" + ALOCACAO_ID
                + "\",\"diaSemana\":\"SEGUNDA\",\"horaInicio\":\"09:00\",\"horaFim\":\"08:00\"}"))
                .andExpect(status().isBadRequest());
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void horarioSobrepostoNaTurmaResponde409ComCodigo() throws Exception {
        HorarioTurma existente = prepararCriacao();
        when(horarioRepository.findAllByEscolaIdAndAlocacao_Turma_IdAndDiaSemana(ESCOLA_A, 1L, DiaSemana.SEGUNDA))
                .thenReturn(List.of(existente));

        mockMvc.perform(json(post(URL), accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_TURMA_CONFLITANTE"));
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void horarioSobrepostoDoProfessorResponde409ComCodigo() throws Exception {
        HorarioTurma existente = prepararCriacao();
        when(horarioRepository.findAllByEscolaIdAndAlocacao_Professor_IdAndDiaSemana(any(), any(),
                eq(DiaSemana.SEGUNDA))).thenReturn(List.of(existente));

        mockMvc.perform(json(post(URL), accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_PROFESSOR_CONFLITANTE"));
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void turmaDeOutraEscolaResponde404() throws Exception {
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.empty());
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.empty());
        String token = accessToken("SECRETARIA", ESCOLA_A);

        mockMvc.perform(com(get(URL), token)).andExpect(status().isNotFound());
        mockMvc.perform(json(post(URL), token, CORPO_VALIDO)).andExpect(status().isNotFound());
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void alocacaoDeOutraTurmaResponde400() throws Exception {
        prepararCriacao();
        Turma outraTurma = Turma.builder().id(2L).nome("2B").build();
        AlocacaoProfessor alocacao = AlocacaoProfessor.builder().id(ALOCACAO_ID).escolaId(ESCOLA_A)
                .turma(outraTurma).build();
        when(alocacaoRepository.findByIdAndEscolaId(ALOCACAO_ID, ESCOLA_A)).thenReturn(Optional.of(alocacao));

        mockMvc.perform(json(post(URL), accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Alocacao nao pertence a turma"));
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void secretariaMudaODiaEOHorarioDaAula() throws Exception {
        HorarioTurma aula = prepararCriacao();
        when(horarioRepository.findByIdAndEscolaIdAndAlocacao_Turma_Id(aula.getId(), ESCOLA_A, 1L))
                .thenReturn(Optional.of(aula));
        // doReturn: o when() chamaria o stub anterior, que troca o id da aula
        doReturn(aula).when(horarioRepository).saveAndFlush(aula);

        mockMvc.perform(json(put(URL + "/" + aula.getId()), accessToken("SECRETARIA", ESCOLA_A), CORPO_AJUSTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaSemana").value("TERCA"))
                .andExpect(jsonPath("$.horaInicio").value("10:00:00"))
                .andExpect(jsonPath("$.horaFim").value("11:00:00"));
    }

    @Test
    void removerResponde204() throws Exception {
        HorarioTurma aula = prepararCriacao();
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(aula.getAlocacao().getTurma()));
        when(horarioRepository.findByIdAndEscolaIdAndAlocacao_Turma_Id(aula.getId(), ESCOLA_A, 1L))
                .thenReturn(Optional.of(aula));

        mockMvc.perform(com(delete(URL + "/" + aula.getId()), accessToken("SECRETARIA", ESCOLA_A)))
                .andExpect(status().isNoContent());
        verify(horarioRepository).delete(aula);
    }

    @Test
    void consultaDaGradeTrazDisciplinaEProfessor() throws Exception {
        HorarioTurma aula = prepararCriacao();
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(aula.getAlocacao().getTurma()));
        when(horarioRepository.findAllByEscolaIdAndAlocacao_Turma_Id(ESCOLA_A, 1L)).thenReturn(List.of(aula));

        mockMvc.perform(com(get(URL), accessToken("MASTER", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].disciplinaNome").value("Matematica"))
                .andExpect(jsonPath("$[0].professorNome").value("Ana"))
                .andExpect(jsonPath("$[0].diaSemana").value("SEGUNDA"));
    }
}
