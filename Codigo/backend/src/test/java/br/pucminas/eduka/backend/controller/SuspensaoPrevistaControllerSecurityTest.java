package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.MotivoSuspensao;
import br.pucminas.eduka.backend.dto.SuspensaoPrevistaResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.SuspensaoPorAtrasoService;

/**
 * Contrato do aviso de suspensao ({@code /config-pagamento/assinatura/suspensao})
 * e do bloqueio de escola suspensa com o token ja emitido
 * ({@code EscolaSuspensaFilter}): filter chain e tokens reais.
 */
@WebMvcTest(SuspensaoPrevistaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class SuspensaoPrevistaControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final String URL = "/config-pagamento/assinatura/suspensao";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private SuspensaoPorAtrasoService suspensaoPorAtrasoService;

    @MockitoBean
    private EscolaRepository escolaRepository;

    private String token(String perfil) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void escolaSuspensa(MotivoSuspensao motivo) {
        when(escolaRepository.situacao(ESCOLA)).thenReturn(Optional.of(new EscolaRepository.Situacao() {
            @Override
            public boolean isAtiva() {
                return false;
            }

            @Override
            public MotivoSuspensao getMotivo() {
                return motivo;
            }
        }));
    }

    @Test
    void soOMasterVeOAviso() throws Exception {
        for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(com(get(URL), token(perfil))).andExpect(status().isForbidden());
        }
        verify(suspensaoPorAtrasoService, never()).prevista(any());
    }

    @Test
    void semSuspensaoPrevistaResponde204() throws Exception {
        when(suspensaoPorAtrasoService.prevista(ESCOLA)).thenReturn(Optional.empty());

        mockMvc.perform(com(get(URL), token("MASTER"))).andExpect(status().isNoContent());
    }

    @Test
    void comSuspensaoPrevistaDevolveODiaEAFatura() throws Exception {
        UUID fatura = UUID.randomUUID();
        when(suspensaoPorAtrasoService.prevista(ESCOLA)).thenReturn(Optional.of(new SuspensaoPrevistaResponse(
                LocalDate.of(2026, 11, 16), 10, fatura, "Plano Avançado", new BigDecimal("690.00"),
                LocalDate.of(2026, 11, 1))));

        mockMvc.perform(com(get(URL), token("MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspensaoEm").value("2026-11-16"))
                .andExpect(jsonPath("$.diasRestantes").value(10))
                .andExpect(jsonPath("$.faturaId").value(fatura.toString()))
                .andExpect(jsonPath("$.faturaValor").value(690.00));
    }

    @Test
    void escolaSuspensaDepoisDoLoginEBarradaEmQualquerRequisicao() throws Exception {
        escolaSuspensa(MotivoSuspensao.FATURA_EM_ATRASO);

        mockMvc.perform(com(get(URL), token("MASTER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ESCOLA_SUSPENSA"))
                .andExpect(jsonPath("$.motivo").value("FATURA_EM_ATRASO"));
        verify(suspensaoPorAtrasoService, never()).prevista(any());
    }

    @Test
    void suspensaoManualTambemBarraComOMotivo() throws Exception {
        escolaSuspensa(MotivoSuspensao.MANUAL);

        mockMvc.perform(com(get(URL), token("MASTER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ESCOLA_SUSPENSA"))
                .andExpect(jsonPath("$.motivo").value("MANUAL"));
    }

    @Test
    void superAdminNaoTemEscolaEPassaDireto() throws Exception {
        mockMvc.perform(com(get(URL), jwtService.generateAccessToken(UUID.randomUUID(), "SUPER_ADMIN", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
        verify(escolaRepository, never()).situacao(any());
    }
}
