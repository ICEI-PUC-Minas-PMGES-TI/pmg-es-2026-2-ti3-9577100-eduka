package br.pucminas.eduka.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.service.EscolaService;

@WebMvcTest(EscolaController.class)
@Import(JwtConfig.class)
class EscolaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private EscolaService escolaService;

    private EscolaResponse escolaResponse(UUID id, String nome) {
        return new EscolaResponse(id, nome, "12.345.678/0001-90", "Rua das Escolas, 100",
                "contato@escola.com", "(31) 4000-0000", true, Instant.now());
    }

    private String tokenComPerfil(String perfil) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(60, ChronoUnit.MINUTES))
                .claim("perfil", perfil)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void retornaListaDeEscolas() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.listar()).thenReturn(List.of(escolaResponse(id, "Escola A")));

        mockMvc.perform(get("/escolas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].nome").value("Escola A"));
    }

    @Test
    void retornaListaVaziaQuandoNaoHaEscolas() throws Exception {
        when(escolaService.listar()).thenReturn(List.of());

        mockMvc.perform(get("/escolas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void retornaDetalheDeUmaEscola() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.buscarPorId(id)).thenReturn(escolaResponse(id, "Escola A"));

        mockMvc.perform(get("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Escola A"));
    }

    @Test
    void retorna404ParaEscolaInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.buscarPorId(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "escola não encontrada"));

        mockMvc.perform(get("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna401AoListarSemToken() throws Exception {
        mockMvc.perform(get("/escolas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retorna403AoListarComPerfilNaoAutorizado() throws Exception {
        mockMvc.perform(get("/escolas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("PROFESSOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void retorna403AoBuscarDetalheComPerfilNaoAutorizado() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("PROFESSOR")))
                .andExpect(status().isForbidden());
    }
}
