package br.pucminas.eduka.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;

@WebMvcTest(HealthController.class)
@Import({ JwtConfig.class, JwtService.class })
class HealthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void healthEndpointIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void authRoutesAreReachableWithoutAuthentication() throws Exception {
        // Nao ha controller mapeado ainda: 404 prova que a requisicao passou pelo
        // filtro de seguranca e chegou ao dispatcher (caso contrario seria 401).
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anyOtherRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteRejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/usuarios")
                .header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteAcceptsValidAccessToken() throws Exception {
        String token = jwtService.generateAccessToken(
                java.util.UUID.randomUUID(), "SECRETARIA", java.util.UUID.randomUUID());

        // 404 em vez de 401: passou pela autenticacao e so nao ha controller na rota.
        mockMvc.perform(get("/usuarios")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
