package br.pucminas.eduka.backend.controller.privacidade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import br.pucminas.eduka.backend.domain.privacidade.AceitePrivacidade;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.privacidade.AceitePrivacidadeRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.privacidade.AvisoPrivacidade;
import br.pucminas.eduka.backend.service.privacidade.PrivacidadeService;

/**
 * Contrato de {@code /privacidade}: qualquer perfil logado le o aviso e
 * aceita; sem token, 401. Filter chain, servico e validacao reais; so o
 * repositorio e mock.
 */
@WebMvcTest(PrivacidadeController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        PrivacidadeService.class })
class PrivacidadeControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AceitePrivacidadeRepository aceiteRepository;

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder aceite(String versao) {
        return post("/privacidade/aceite").contentType(MediaType.APPLICATION_JSON)
                .content("{\"versao\":\"%s\"}".formatted(versao));
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/privacidade/aviso")).andExpect(status().isUnauthorized());
        mockMvc.perform(aceite(AvisoPrivacidade.VERSAO)).andExpect(status().isUnauthorized());
    }

    @Test
    void todoPerfilLeOAvisoComAVersaoEOTexto() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA);
            mockMvc.perform(com(get("/privacidade/aviso"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.versao").value(AvisoPrivacidade.VERSAO))
                    .andExpect(jsonPath("$.resumo.length()").value(AvisoPrivacidade.RESUMO.size()))
                    .andExpect(jsonPath("$.secoes[0].titulo").isString())
                    .andExpect(jsonPath("$.aceitoEm").doesNotExist());
        }
        String superAdmin = jwtService.generateAccessToken(UUID.randomUUID(), "SUPER_ADMIN", null);
        mockMvc.perform(com(get("/privacidade/aviso"), superAdmin)).andExpect(status().isOk());
    }

    @Test
    void quemJaAceitouVeADataDoAceite() throws Exception {
        UUID usuario = UUID.randomUUID();
        Instant aceitoEm = Instant.parse("2026-11-11T13:00:00Z");
        when(aceiteRepository.findByUsuarioIdAndVersao(usuario, AvisoPrivacidade.VERSAO)).thenReturn(Optional.of(
                AceitePrivacidade.builder().usuarioId(usuario).versao(AvisoPrivacidade.VERSAO).aceitoEm(aceitoEm)
                        .build()));

        mockMvc.perform(com(get("/privacidade/aviso"), jwtService.generateAccessToken(usuario, "ALUNO", ESCOLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceitoEm").value("2026-11-11T13:00:00Z"));
    }

    @Test
    void aceitaAVersaoEmVigorERecusaOutra() throws Exception {
        UUID usuario = UUID.randomUUID();
        String token = jwtService.generateAccessToken(usuario, "PROFESSOR", ESCOLA);
        when(aceiteRepository.saveAndFlush(any(AceitePrivacidade.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(com(aceite(AvisoPrivacidade.VERSAO), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceitoEm").isString());

        mockMvc.perform(com(aceite("0.9"), token)).andExpect(status().isBadRequest());
        mockMvc.perform(com(post("/privacidade/aceite").contentType(MediaType.APPLICATION_JSON).content("{}"), token))
                .andExpect(status().isBadRequest());
        verify(aceiteRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void aceitarDeNovoNaoGravaOutroAceite() throws Exception {
        UUID usuario = UUID.randomUUID();
        when(aceiteRepository.findByUsuarioIdAndVersao(usuario, AvisoPrivacidade.VERSAO)).thenReturn(Optional.of(
                AceitePrivacidade.builder().usuarioId(usuario).versao(AvisoPrivacidade.VERSAO)
                        .aceitoEm(Instant.parse("2026-11-10T10:00:00Z")).build()));

        mockMvc.perform(com(aceite(AvisoPrivacidade.VERSAO), jwtService.generateAccessToken(usuario, "ALUNO", ESCOLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceitoEm").value("2026-11-10T10:00:00Z"));
        verify(aceiteRepository, never()).saveAndFlush(any());
    }
}
