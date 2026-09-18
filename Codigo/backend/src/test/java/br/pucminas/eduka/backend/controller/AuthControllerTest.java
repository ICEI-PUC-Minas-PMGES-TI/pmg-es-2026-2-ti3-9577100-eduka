package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.service.AuthService;

@WebMvcTest(AuthController.class)
@Import(JwtConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void retornaTokensParaCredenciaisValidas() throws Exception {
        when(authService.login("usuario@escola.com", "senha-correta"))
                .thenReturn(new LoginResponse("access-token", "refresh-token"));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"usuario@escola.com","senha":"senha-correta"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void retorna400ParaCamposEmBranco() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"","senha":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna401ParaCredenciaisInvalidas() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciais inválidas"));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"usuario@escola.com","senha":"senha-errada"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retorna403ParaUsuarioInativo() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "usuário inativo"));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"usuario@escola.com","senha":"senha-correta"}
                                """))
                .andExpect(status().isForbidden());
    }
}
