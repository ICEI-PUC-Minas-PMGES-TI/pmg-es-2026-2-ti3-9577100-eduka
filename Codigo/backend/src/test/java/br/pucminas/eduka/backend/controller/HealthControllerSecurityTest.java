package br.pucminas.eduka.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.security.JwtConfig;

@WebMvcTest(HealthController.class)
@Import(JwtConfig.class)
class HealthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
