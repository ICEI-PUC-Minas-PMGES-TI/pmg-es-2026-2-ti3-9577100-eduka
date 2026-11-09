package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.UsoEscolaRepository;
import br.pucminas.eduka.backend.repository.UsoEscolaRepository.UsoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.UsoEscolaService;

/**
 * Contrato de {@code /escolas/uso}: filter chain, tokens, {@code @PreAuthorize}
 * e servico reais; so o repositorio e mock. As contas ficam em
 * {@code UsoEscolaBancoRealTest}.
 */
@WebMvcTest(UsoEscolaController.class)
@Import({ JwtConfig.class, JwtService.class, ApiExceptionHandler.class, UsoEscolaService.class })
class UsoEscolaControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsoEscolaRepository usoEscolaRepository;

    private String token(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static UsoEscola uso(long alunos, long usuarios, long cobrancas, Instant ultimaAtividade) {
        return new UsoEscola() {
            @Override
            public UUID getEscolaId() {
                return ESCOLA;
            }

            @Override
            public long getAlunos() {
                return alunos;
            }

            @Override
            public long getUsuariosAtivos() {
                return usuarios;
            }

            @Override
            public long getCobrancasNoMes() {
                return cobrancas;
            }

            @Override
            public Instant getUltimaAtividade() {
                return ultimaAtividade;
            }
        };
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/escolas/uso")).andExpect(status().isUnauthorized());
    }

    @Test
    void soOSuperAdminVeOUsoDasEscolas() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(com(get("/escolas/uso"), token(perfil, ESCOLA))).andExpect(status().isForbidden());
        }
        verify(usoEscolaRepository, never()).usoPorEscola(any(), any());
    }

    @Test
    void devolveOUsoDeCadaEscola() throws Exception {
        when(usoEscolaRepository.usoPorEscola(any(), any()))
                .thenReturn(List.of(uso(120, 8, 95, Instant.parse("2026-11-04T18:30:00Z"))));

        mockMvc.perform(com(get("/escolas/uso"), token("SUPER_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].escolaId").value(ESCOLA.toString()))
                .andExpect(jsonPath("$[0].alunos").value(120))
                .andExpect(jsonPath("$[0].usuariosAtivos").value(8))
                .andExpect(jsonPath("$[0].cobrancasNoMes").value(95))
                .andExpect(jsonPath("$[0].mes").exists())
                .andExpect(jsonPath("$[0].ultimaAtividade").value("2026-11-04T18:30:00Z"));
    }

    @Test
    void escolaSemAtividadeVemComUltimaAtividadeNula() throws Exception {
        when(usoEscolaRepository.usoPorEscola(any(), any())).thenReturn(List.of(uso(0, 0, 0, null)));

        mockMvc.perform(com(get("/escolas/uso"), token("SUPER_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ultimaAtividade").doesNotExist());
    }
}
