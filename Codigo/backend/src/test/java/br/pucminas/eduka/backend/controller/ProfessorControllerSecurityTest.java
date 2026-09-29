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
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;
import br.pucminas.eduka.backend.service.AcessoService;
import br.pucminas.eduka.backend.service.ProfessorService;

/**
 * Contrato de seguranca de {@code /professores}, ponta a ponta: filter chain,
 * tokens, {@code @PreAuthorize}, validacao e servico reais; so os
 * repositorios sao mock.
 */
@WebMvcTest(ProfessorController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        ProfessorService.class, AcessoService.class, PasswordEncoderConfig.class })
class ProfessorControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();
    private static final String CORPO_VALIDO =
            "{\"nome\":\"Ana\",\"cpf\":\"52998224725\",\"email\":\"ana@escola.com\"}";

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
    @MockitoBean
    private UsuarioRepository usuarioRepository;

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
        return Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome(nome).cpf("52998224725")
                .email("ana@escola.com").ativo(true).build();
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
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario usuario = inv.getArgument(0);
            usuario.setId(UUID.randomUUID());
            return usuario;
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
        void semNomeOuComCpfInvalidoResponde400() throws Exception {
            String token = accessToken("SECRETARIA", ESCOLA_A);
            mockMvc.perform(json(post("/professores"), token, "{\"cpf\":\"52998224725\",\"email\":\"a@b.com\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/professores"), token,
                    "{\"nome\":\"Ana\",\"cpf\":\"123.456.789-00\",\"email\":\"a@b.com\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/professores"), token,
                    "{\"nome\":\"Ana\",\"cpf\":\"52998224724\",\"email\":\"a@b.com\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/professores"), token,
                    "{\"nome\":\"Ana\",\"cpf\":\"11111111111\",\"email\":\"a@b.com\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/professores"), token,
                    "{\"nome\":\"Ana\",\"cpf\":\"52998224725\",\"email\":\"nao-e-email\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/professores"), token, "{\"nome\":\"Ana\",\"cpf\":\"52998224725\"}"))
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
                    "{\"nome\":\"Ana\",\"cpf\":\"52998224725\",\"email\":\"ana@escola.com\",\"escolaId\":\""
                            + ESCOLA_B + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.escolaId").value(ESCOLA_A.toString()));

            ArgumentCaptor<Professor> captor = ArgumentCaptor.forClass(Professor.class);
            verify(professorRepository).save(captor.capture());
            assertThat(captor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        }
    }

    @Nested
    class PrimeiroAcesso {

        @Test
        void cadastroCriaLoginPeloEmailComSenhaProvisoria() throws Exception {
            stubSaves();

            mockMvc.perform(json(post("/professores"), accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                    .andExpect(status().isCreated());

            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            Usuario usuario = captor.getValue();
            assertThat(usuario.getLogin()).isEqualTo("ana@escola.com");
            assertThat(usuario.getEscolaId()).isEqualTo(ESCOLA_A);
            assertThat(usuario.isSenhaProvisoria()).isTrue();
            assertThat(usuario.getSenhaProvisoriaExpiraEm()).isNotNull();
            assertThat(usuario.getSenhaHash()).doesNotContain("52998224725");
        }

        @Test
        void emailJaUsadoComoLoginResponde409ENaoGravaProfessor() throws Exception {
            stubSaves();
            when(usuarioRepository.existsByLogin("ana@escola.com")).thenReturn(true);

            mockMvc.perform(json(post("/professores"), accessToken("SECRETARIA", ESCOLA_A), CORPO_VALIDO))
                    .andExpect(status().isConflict());
            verify(professorRepository, never()).save(any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void reenvioSoParaGestaoESecretaria() throws Exception {
            String url = "/professores/" + UUID.randomUUID() + "/primeiro-acesso";
            for (String perfil : List.of("PROFESSOR", "FINANCEIRO")) {
                mockMvc.perform(com(post(url), accessToken(perfil, ESCOLA_A)))
                        .andExpect(status().isForbidden());
            }
            verify(professorRepository, never()).findByIdAndEscolaId(any(), any());
        }

        @Test
        void reenvioDeQuemJaDefiniuASenhaResponde409() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            Professor ana = professor(ESCOLA_A, pessoaAna.getId());
            when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
            when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
            when(usuarioRepository.findByPessoaId(pessoaAna.getId())).thenReturn(Optional.of(Usuario.builder()
                    .id(UUID.randomUUID()).login("ana@escola.com").senhaProvisoria(false).ativo(true).build()));

            mockMvc.perform(com(post("/professores/" + ana.getId() + "/primeiro-acesso"),
                    accessToken("SECRETARIA", ESCOLA_A)))
                    .andExpect(status().isConflict());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void reenvioComSenhaAindaProvisoriaResponde204() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            Professor ana = professor(ESCOLA_A, pessoaAna.getId());
            Usuario usuario = Usuario.builder().id(UUID.randomUUID()).login("ana@escola.com")
                    .senhaProvisoria(true).ativo(true).build();
            ana.setUsuarioId(usuario.getId());
            when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
            when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
            when(usuarioRepository.findByPessoaId(pessoaAna.getId())).thenReturn(Optional.of(usuario));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(com(post("/professores/" + ana.getId() + "/primeiro-acesso"),
                    accessToken("SECRETARIA", ESCOLA_A)))
                    .andExpect(status().isNoContent());
            assertThat(usuario.getSenhaProvisoriaExpiraEm()).isNotNull();
        }
    }
}
