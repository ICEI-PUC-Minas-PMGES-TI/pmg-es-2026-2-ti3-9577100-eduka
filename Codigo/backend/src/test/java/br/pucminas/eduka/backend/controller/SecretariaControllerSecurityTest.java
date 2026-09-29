package br.pucminas.eduka.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;
import br.pucminas.eduka.backend.service.SecretariaService;

/**
 * Contrato de seguranca de {@code /usuarios/secretarias}, ponta a ponta:
 * filter chain, tokens, {@code @PreAuthorize}, validacao e servico reais; so
 * os repositorios sao mock.
 */
@WebMvcTest(SecretariaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        SecretariaService.class, PasswordEncoderConfig.class })
class SecretariaControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();
    private static final String CORPO_VALIDO = "{\"nome\":\"Ana\",\"cpf\":\"11122233344\","
            + "\"login\":\"ana.secretaria\",\"senha\":\"SenhaForte123\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;
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

    private static Usuario secretaria(UUID escolaId, UUID pessoaId) {
        return Usuario.builder().id(UUID.randomUUID()).escolaId(escolaId).pessoaId(pessoaId)
                .login("secretaria").senhaHash("{bcrypt}hash").perfil(PerfilUsuario.SECRETARIA).ativo(true).build();
    }

    private static Pessoa pessoa(String nome) {
        return Pessoa.builder().id(UUID.randomUUID()).nome(nome).cpf("11122233344").ativo(true).build();
    }

    private void stubSaves() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario usuario = inv.getArgument(0);
            usuario.setId(UUID.randomUUID());
            return usuario;
        });
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/usuarios/secretarias"))
                .andExpect(status().isUnauthorized());
        verify(usuarioRepository, never()).findAllByEscolaIdAndPerfil(any(), any());
    }

    @Nested
    class AutorizacaoPorPerfil {

        @Test
        void perfisDeEscolaNaoMasterNaoAcessam() throws Exception {
            for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR")) {
                String token = accessToken(perfil, ESCOLA_A);
                mockMvc.perform(com(get("/usuarios/secretarias"), token))
                        .andExpect(status().isForbidden());
                mockMvc.perform(json(post("/usuarios/secretarias"), token, CORPO_VALIDO))
                        .andExpect(status().isForbidden());
            }
            verify(usuarioRepository, never()).findAllByEscolaIdAndPerfil(any(), any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void alunoESuperAdminNaoAcessam() throws Exception {
            mockMvc.perform(com(get("/usuarios/secretarias"), accessToken("ALUNO", ESCOLA_A)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(post("/usuarios/secretarias"), accessToken("ALUNO", ESCOLA_A), CORPO_VALIDO))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(get("/usuarios/secretarias"), accessToken("SUPER_ADMIN", null)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(post("/usuarios/secretarias"), accessToken("SUPER_ADMIN", null), CORPO_VALIDO))
                    .andExpect(status().isForbidden());
            verify(usuarioRepository, never()).findAllByEscolaIdAndPerfil(any(), any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void masterCadastra() throws Exception {
            stubSaves();
            when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(List.of());

            mockMvc.perform(json(post("/usuarios/secretarias"), accessToken("MASTER", ESCOLA_A), CORPO_VALIDO))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(HttpHeaders.LOCATION))
                    .andExpect(jsonPath("$.escolaId").value(ESCOLA_A.toString()))
                    .andExpect(jsonPath("$.nome").value("Ana"))
                    .andExpect(jsonPath("$.ativo").value(true));
        }
    }

    @Nested
    class Validacao {

        @Test
        void semNomeLoginOuSenhaOuComCpfMalformadoResponde400() throws Exception {
            String token = accessToken("MASTER", ESCOLA_A);
            mockMvc.perform(json(post("/usuarios/secretarias"), token,
                    "{\"cpf\":\"11122233344\",\"login\":\"ana\",\"senha\":\"SenhaForte123\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/usuarios/secretarias"), token,
                    "{\"nome\":\"Ana\",\"cpf\":\"11122233344\",\"senha\":\"SenhaForte123\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/usuarios/secretarias"), token,
                    "{\"nome\":\"Ana\",\"cpf\":\"11122233344\",\"login\":\"ana\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/usuarios/secretarias"), token,
                    "{\"nome\":\"Ana\",\"cpf\":\"123.456.789-00\",\"login\":\"ana\",\"senha\":\"SenhaForte123\"}"))
                    .andExpect(status().isBadRequest());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void loginRepetidoResponde409() throws Exception {
            when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(List.of());
            when(usuarioRepository.existsByLogin("ana.secretaria")).thenReturn(true);

            mockMvc.perform(json(post("/usuarios/secretarias"), accessToken("MASTER", ESCOLA_A), CORPO_VALIDO))
                    .andExpect(status().isConflict());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void cpfRepetidoNaEscolaResponde409() throws Exception {
            Pessoa existente = pessoa("Outra");
            when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(List.of(secretaria(ESCOLA_A, existente.getId())));
            when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(existente));

            mockMvc.perform(json(post("/usuarios/secretarias"), accessToken("MASTER", ESCOLA_A), CORPO_VALIDO))
                    .andExpect(status().isConflict());
            verify(usuarioRepository, never()).save(any());
        }
    }

    @Nested
    class IsolamentoEntreEscolas {

        @Test
        void listagemUsaEscolaDoToken() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(List.of(secretaria(ESCOLA_A, pessoaAna.getId())));
            when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));

            mockMvc.perform(com(get("/usuarios/secretarias?nome=an&ativo=true"), accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nome").value("Ana"))
                    .andExpect(jsonPath("$[0].cpf").doesNotExist());

            verify(usuarioRepository).findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA);
            verify(usuarioRepository, never()).findAll();
        }

        @Test
        void secretariaDeOutraEscolaResponde404EmTodaOperacao() throws Exception {
            Usuario daEscolaB = secretaria(ESCOLA_B, UUID.randomUUID());
            when(usuarioRepository.findByIdAndEscolaIdAndPerfil(daEscolaB.getId(), ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(Optional.empty());
            String token = accessToken("MASTER", ESCOLA_A);
            String url = "/usuarios/secretarias/" + daEscolaB.getId();

            mockMvc.perform(com(get(url), token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Secretaria nao encontrada"));
            mockMvc.perform(json(put(url), token, CORPO_VALIDO))
                    .andExpect(status().isNotFound());
            mockMvc.perform(com(delete(url), token))
                    .andExpect(status().isNotFound());
            mockMvc.perform(com(patch(url + "/ativar"), token))
                    .andExpect(status().isNotFound());

            verify(usuarioRepository, never()).findById(any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void inativarSecretariaDaPropriaEscolaResponde204() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            Usuario daEscolaA = secretaria(ESCOLA_A, pessoaAna.getId());
            when(usuarioRepository.findByIdAndEscolaIdAndPerfil(daEscolaA.getId(), ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(Optional.of(daEscolaA));
            when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A))
                    .thenReturn(Optional.of(pessoaAna));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(com(delete("/usuarios/secretarias/" + daEscolaA.getId()), accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isNoContent());

            assertThat(daEscolaA.isAtivo()).isFalse();
            verify(usuarioRepository, never()).delete(any());
        }

        @Test
        void ativarSecretariaDaPropriaEscolaResponde200() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            Usuario daEscolaA = secretaria(ESCOLA_A, pessoaAna.getId());
            daEscolaA.setAtivo(false);
            when(usuarioRepository.findByIdAndEscolaIdAndPerfil(daEscolaA.getId(), ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(Optional.of(daEscolaA));
            when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A))
                    .thenReturn(Optional.of(pessoaAna));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(com(patch("/usuarios/secretarias/" + daEscolaA.getId() + "/ativar"),
                    accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(true));

            assertThat(daEscolaA.isAtivo()).isTrue();
        }

        @Test
        void atualizarSecretariaDaPropriaEscolaResponde200ComDadosNovos() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            Usuario daEscolaA = secretaria(ESCOLA_A, pessoaAna.getId());
            when(usuarioRepository.findByIdAndEscolaIdAndPerfil(daEscolaA.getId(), ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(Optional.of(daEscolaA));
            when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A))
                    .thenReturn(Optional.of(pessoaAna));
            when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                    .thenReturn(List.of(daEscolaA));
            when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));
            when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(json(put("/usuarios/secretarias/" + daEscolaA.getId()), accessToken("MASTER", ESCOLA_A),
                    "{\"nome\":\"Ana Paula\",\"cpf\":\"11122233344\",\"email\":\"ana@escola.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Ana Paula"))
                    .andExpect(jsonPath("$.email").value("ana@escola.com"));

            ArgumentCaptor<Pessoa> captor = ArgumentCaptor.forClass(Pessoa.class);
            verify(pessoaRepository).save(captor.capture());
            assertThat(captor.getValue().getNome()).isEqualTo("Ana Paula");
        }
    }
}
