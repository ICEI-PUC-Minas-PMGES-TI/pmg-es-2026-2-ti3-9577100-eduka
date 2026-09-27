package br.pucminas.eduka.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.ProfessorService;

/**
 * Contrato de seguranca de {@code /professores}, ponta a ponta: filter chain,
 * tokens, {@code @PreAuthorize}, validacao e servico reais; so os
 * repositorios sao mock.
 */
@WebMvcTest(ProfessorController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        ProfessorService.class })
class ProfessorControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();
    private static final String CORPO_VALIDO = "{\"nome\":\"Ana\",\"cpf\":\"12345678900\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ProfessorRepository professorRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private PessoaPapelRepository pessoaPapelRepository;

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

    private static Professor professor(UUID escolaId, UUID pessoaId) {
        return Professor.builder().id(UUID.randomUUID()).escolaId(escolaId).pessoaId(pessoaId).ativo(true).build();
    }

    private static Pessoa pessoa(String nome) {
        return Pessoa.builder().id(UUID.randomUUID()).nome(nome).cpf("12345678900").ativo(true).build();
    }

    private void stubSaves() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(professorRepository.save(any(Professor.class))).thenAnswer(inv -> {
            Professor professor = inv.getArgument(0);
            professor.setId(UUID.randomUUID());
            return professor;
        });
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/professores"))
                .andExpect(status().isUnauthorized());
        verify(professorRepository, never()).findAllByEscolaId(any());
    }

    @Nested
    class AutorizacaoPorPerfil {

        @Test
        void professorEFinanceiroNaoAcessam() throws Exception {
            for (String perfil : List.of("PROFESSOR", "FINANCEIRO")) {
                String token = accessToken(perfil, ESCOLA_A);
                mockMvc.perform(com(get("/professores"), token))
                        .andExpect(status().isForbidden());
                mockMvc.perform(json(post("/professores"), token, CORPO_VALIDO))
                        .andExpect(status().isForbidden());
            }
            verify(professorRepository, never()).findAllByEscolaId(any());
            verify(professorRepository, never()).save(any());
        }

        @Test
        void alunoESuperAdminNaoAcessam() throws Exception {
            mockMvc.perform(com(get("/professores"), accessToken("ALUNO", ESCOLA_A)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(get("/professores"), accessToken("SUPER_ADMIN", null)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(delete("/professores/" + UUID.randomUUID()), accessToken("SUPER_ADMIN", null)))
                    .andExpect(status().isForbidden());
            verify(professorRepository, never()).findAllByEscolaId(any());
            verify(professorRepository, never()).findByIdAndEscolaId(any(), any());
        }

        @Test
        void secretariaCadastra() throws Exception {
            stubSaves();

            mockMvc.perform(json(post("/professores"), accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(HttpHeaders.LOCATION))
                    .andExpect(jsonPath("$.escolaId").value(ESCOLA_A.toString()))
                    .andExpect(jsonPath("$.ativo").value(true));
        }
    }

    @Nested
    class Validacao {

        @Test
        void semNomeOuComCpfMalformadoResponde400() throws Exception {
            String token = accessToken("SECRETARIA", ESCOLA_A);
            mockMvc.perform(json(post("/professores"), token, "{\"cpf\":\"12345678900\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/professores"), token, "{\"nome\":\"Ana\",\"cpf\":\"123.456.789-00\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/professores"), token,
                    "{\"nome\":\"Ana\",\"cpf\":\"12345678900\",\"email\":\"nao-e-email\"}"))
                    .andExpect(status().isBadRequest());
            verify(professorRepository, never()).save(any());
        }

        @Test
        void cpfRepetidoNaEscolaResponde409() throws Exception {
            Pessoa existente = pessoa("Ana");
            when(professorRepository.findAllByEscolaId(ESCOLA_A))
                    .thenReturn(List.of(professor(ESCOLA_A, existente.getId())));
            when(pessoaRepository.findAllByIdInAndEscolaId(any(), any())).thenReturn(List.of(existente));

            mockMvc.perform(json(post("/professores"), accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                    .andExpect(status().isConflict());
            verify(professorRepository, never()).save(any());
        }
    }

    @Nested
    class IsolamentoEntreEscolas {

        @Test
        void listagemUsaEscolaDoToken() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            when(professorRepository.findAllByEscolaId(ESCOLA_A))
                    .thenReturn(List.of(professor(ESCOLA_A, pessoaAna.getId())));
            when(pessoaRepository.findAllByIdInAndEscolaId(any(), any())).thenReturn(List.of(pessoaAna));

            mockMvc.perform(com(get("/professores?nome=an&ativo=true"), accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nome").value("Ana"))
                    .andExpect(jsonPath("$[0].cpf").doesNotExist());

            verify(professorRepository).findAllByEscolaId(ESCOLA_A);
            verify(professorRepository, never()).findAll();
        }

        @Test
        void professorDeOutraEscolaResponde404EmTodaOperacao() throws Exception {
            Professor daEscolaB = professor(ESCOLA_B, UUID.randomUUID());
            when(professorRepository.findByIdAndEscolaId(daEscolaB.getId(), ESCOLA_A)).thenReturn(Optional.empty());
            String token = accessToken("SECRETARIA", ESCOLA_A);
            String url = "/professores/" + daEscolaB.getId();

            mockMvc.perform(com(get(url), token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Professor nao encontrado"));
            mockMvc.perform(json(put(url), token, CORPO_VALIDO))
                    .andExpect(status().isNotFound());
            mockMvc.perform(com(delete(url), token))
                    .andExpect(status().isNotFound());

            verify(professorRepository, never()).findById(any());
            verify(professorRepository, never()).save(any());
        }

        @Test
        void inativarProfessorDaPropriaEscolaResponde204() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            Professor daEscolaA = professor(ESCOLA_A, pessoaAna.getId());
            when(professorRepository.findByIdAndEscolaId(daEscolaA.getId(), ESCOLA_A))
                    .thenReturn(Optional.of(daEscolaA));
            when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
            when(professorRepository.save(any(Professor.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(com(delete("/professores/" + daEscolaA.getId()), accessToken("SECRETARIA", ESCOLA_A)))
                    .andExpect(status().isNoContent());

            assertThat(daEscolaA.isAtivo()).isFalse();
            verify(professorRepository, never()).delete(any());
        }

        @Test
        void escolaIdNoBodyEhIgnoradoNoCadastro() throws Exception {
            stubSaves();

            mockMvc.perform(json(post("/professores"), accessToken("MASTER", ESCOLA_A),
                    "{\"nome\":\"Ana\",\"cpf\":\"12345678900\",\"escolaId\":\"" + ESCOLA_B + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.escolaId").value(ESCOLA_A.toString()));

            ArgumentCaptor<Professor> captor = ArgumentCaptor.forClass(Professor.class);
            verify(professorRepository).save(captor.capture());
            assertThat(captor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        }
    }
}
