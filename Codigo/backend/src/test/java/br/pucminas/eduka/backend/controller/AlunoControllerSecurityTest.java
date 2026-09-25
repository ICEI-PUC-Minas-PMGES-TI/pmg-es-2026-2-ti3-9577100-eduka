// Codigo/backend/src/test/java/br/pucminas/eduka/backend/controller/AlunoControllerSecurityTest.java
package br.pucminas.eduka.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.AlunoService;

/**
 * Contrato de seguranca do primeiro recurso de negocio, ponta a ponta: filter
 * chain real, tokens reais, {@code @PreAuthorize} real e servico real. Os
 * repositorios sao mock, para provar que o filtro de escola chega ate a
 * consulta.
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
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private PessoaPapelRepository pessoaPapelRepository;
    @MockitoBean
    private AlunoResponsavelRepository alunoResponsavelRepository;

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

    private static Aluno aluno(UUID escolaId, UUID pessoaId) {
        return Aluno.builder().id(UUID.randomUUID()).escolaId(escolaId).pessoaId(pessoaId)
                .matricula("2026-001").build();
    }

    private static Pessoa pessoa(String nome) {
        return Pessoa.builder().id(UUID.randomUUID()).nome(nome).ativo(true).build();
    }

    private void stubSavePessoaEAluno() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> {
            Aluno aluno = inv.getArgument(0);
            aluno.setId(UUID.randomUUID());
            return aluno;
        });
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
        void superAdminNaoAcessaAlunos() throws Exception {
            // Token real do super admin: sem escola.
            String token = accessToken("SUPER_ADMIN", null);

            mockMvc.perform(getCom("/alunos", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(getCom("/alunos/" + UUID.randomUUID(), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(postCom("/alunos", token, "{\"nome\":\"Ana\",\"matricula\":\"2026-001\"}"))
                    .andExpect(status().isForbidden());

            verify(alunoRepository, never()).findAll();
            verify(alunoRepository, never()).findById(any());
            verify(alunoRepository, never()).save(any());
        }

        @Test
        void secretariaCadastra() throws Exception {
            stubSavePessoaEAluno();

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
        void listagemUsaEscolaDoTokenEAceitaFiltroDeNome() throws Exception {
            Pessoa pessoaAna = pessoa("Ana");
            when(alunoRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(aluno(ESCOLA_A, pessoaAna.getId())));
            when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));

            mockMvc.perform(getCom("/alunos?nome=an", accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].escolaId").value(ESCOLA_A.toString()))
                    .andExpect(jsonPath("$[0].nome").value("Ana"));

            verify(alunoRepository).findAllByEscolaId(ESCOLA_A);
            verify(alunoRepository, never()).findAll();
        }

        @Test
        void alunoDeOutraEscolaResponde404SemRevelarExistencia() throws Exception {
            Aluno daEscolaB = aluno(ESCOLA_B, UUID.randomUUID());
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
            Pessoa pessoaAna = pessoa("Ana");
            Aluno daEscolaA = aluno(ESCOLA_A, pessoaAna.getId());
            when(alunoRepository.findByIdAndEscolaId(daEscolaA.getId(), ESCOLA_A)).thenReturn(Optional.of(daEscolaA));
            when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
            when(alunoResponsavelRepository.findAllById_AlunoId(daEscolaA.getId())).thenReturn(List.of());

            mockMvc.perform(getCom("/alunos/" + daEscolaA.getId(), accessToken("SECRETARIA", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(daEscolaA.getId().toString()));
        }

        @Test
        void escolaIdNoBodyEhIgnoradoNoCadastro() throws Exception {
            stubSavePessoaEAluno();

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
