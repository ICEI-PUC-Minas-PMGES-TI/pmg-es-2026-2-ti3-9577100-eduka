package br.pucminas.eduka.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.AlunoService;

/**
 * Contrato de seguranca do primeiro recurso de negocio, ponta a ponta: filter
 * chain real, tokens reais, {@code @PreAuthorize} real e servico real. So o
 * repositorio e mock, para provar que o filtro de escola chega ate a consulta.
 */
@WebMvcTest(AlunoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        AlunoService.class })
class AlunoControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AlunoRepository alunoRepository;

    private String accessToken(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder getCom(String url, String token) {
        return get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder postCom(String url, String token, String json) {
        return post(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private static Aluno aluno(UUID escolaId) {
        return Aluno.builder()
                .id(UUID.randomUUID())
                .escolaId(escolaId)
                .nome("Ana")
                .matricula("2026-001")
                .build();
    }

    @Nested
    class Autenticacao {

        @Test
        void semTokenResponde401() throws Exception {
            mockMvc.perform(get("/alunos"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
            verify(alunoRepository, never()).findAllByEscolaId(any());
        }

        @Test
        void tokenInvalidoResponde401() throws Exception {
            mockMvc.perform(getCom("/alunos", "nao.e.um.jwt"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class AutorizacaoPorPerfil {

        @Test
        void perfilAlunoNaoListaAlunos() throws Exception {
            mockMvc.perform(getCom("/alunos", accessToken("ALUNO", ESCOLA_A)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("Acesso negado"));
            verify(alunoRepository, never()).findAllByEscolaId(any());
        }

        @Test
        void professorListaMasNaoCadastra() throws Exception {
            when(alunoRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of());

            mockMvc.perform(getCom("/alunos", accessToken("PROFESSOR", ESCOLA_A)))
                    .andExpect(status().isOk());

            mockMvc.perform(postCom("/alunos", accessToken("PROFESSOR", ESCOLA_A),
                    "{\"nome\":\"Ana\",\"matricula\":\"2026-001\"}"))
                    .andExpect(status().isForbidden());
            verify(alunoRepository, never()).save(any());
        }

        @Test
        void secretariaCadastra() throws Exception {
            when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> {
                Aluno a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });

            mockMvc.perform(postCom("/alunos", accessToken("SECRETARIA", ESCOLA_A),
                    "{\"nome\":\"Ana\",\"matricula\":\"2026-001\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(HttpHeaders.LOCATION))
                    .andExpect(jsonPath("$.escolaId").value(ESCOLA_A.toString()));
        }
    }

    @Nested
    class IsolamentoEntreEscolas {

        @Test
        void listagemUsaEscolaDoToken() throws Exception {
            when(alunoRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(aluno(ESCOLA_A)));

            mockMvc.perform(getCom("/alunos", accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].escolaId").value(ESCOLA_A.toString()));

            verify(alunoRepository).findAllByEscolaId(ESCOLA_A);
            verify(alunoRepository, never()).findAll();
        }

        @Test
        void alunoDeOutraEscolaResponde404SemRevelarExistencia() throws Exception {
            Aluno daEscolaB = aluno(ESCOLA_B);
            // O registro existe no banco, mas nao para a escola A.
            when(alunoRepository.findById(daEscolaB.getId())).thenReturn(Optional.of(daEscolaB));
            when(alunoRepository.findByIdAndEscolaId(daEscolaB.getId(), ESCOLA_A)).thenReturn(Optional.empty());

            mockMvc.perform(getCom("/alunos/" + daEscolaB.getId(), accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Aluno nao encontrado"));

            verify(alunoRepository, never()).findById(any());
        }

        @Test
        void alunoDaPropriaEscolaResponde200() throws Exception {
            Aluno daEscolaA = aluno(ESCOLA_A);
            when(alunoRepository.findByIdAndEscolaId(daEscolaA.getId(), ESCOLA_A)).thenReturn(Optional.of(daEscolaA));

            mockMvc.perform(getCom("/alunos/" + daEscolaA.getId(), accessToken("SECRETARIA", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(daEscolaA.getId().toString()));
        }

        @Test
        void superAdminAcessaAlunoDeQualquerEscola() throws Exception {
            Aluno daEscolaB = aluno(ESCOLA_B);
            when(alunoRepository.findById(daEscolaB.getId())).thenReturn(Optional.of(daEscolaB));

            mockMvc.perform(getCom("/alunos/" + daEscolaB.getId(), accessToken("SUPER_ADMIN", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.escolaId").value(ESCOLA_B.toString()));
        }

        @Test
        void escolaIdNoBodyEhIgnoradoNoCadastro() throws Exception {
            when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> {
                Aluno a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });

            mockMvc.perform(postCom("/alunos", accessToken("MASTER", ESCOLA_A),
                    "{\"nome\":\"Ana\",\"matricula\":\"2026-001\",\"escolaId\":\"" + ESCOLA_B + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.escolaId").value(ESCOLA_A.toString()));

            ArgumentCaptor<Aluno> captor = ArgumentCaptor.forClass(Aluno.class);
            verify(alunoRepository).save(captor.capture());
            assertThat(captor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        }
    }
}
