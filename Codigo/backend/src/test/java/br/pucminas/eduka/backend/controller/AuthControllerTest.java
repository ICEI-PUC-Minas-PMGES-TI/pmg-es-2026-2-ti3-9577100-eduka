package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.exception.SenhaProvisoriaExpiradaException;
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
                .thenReturn(LoginResponse.autenticado("access-token", "refresh-token", UUID.randomUUID(),
                        PerfilUsuario.SECRETARIA));

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
                .thenThrow(new CredenciaisInvalidasException());

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"usuario@escola.com","senha":"senha-errada"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retorna401ParaUsuarioInativo() throws Exception {
        // Usuario inativo recebe o mesmo erro generico de credenciais invalidas:
        // distinguir os casos revelaria que o login existe.
        when(authService.login(anyString(), anyString()))
                .thenThrow(new CredenciaisInvalidasException());

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"usuario@escola.com","senha":"senha-correta"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginComSenhaProvisoriaRespondeSemTokens() throws Exception {
        when(authService.login("prof@escola.com", "52998224725"))
                .thenReturn(LoginResponse.trocaDeSenhaPendente(UUID.randomUUID(), PerfilUsuario.PROFESSOR));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"prof@escola.com","senha":"52998224725"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void primeiroAcessoEhPublicoEDevolveTokens() throws Exception {
        when(authService.definirSenhaDefinitiva("prof@escola.com", "52998224725", "nova-senha-forte"))
                .thenReturn(LoginResponse.autenticado("access-token", "refresh-token", UUID.randomUUID(),
                        PerfilUsuario.PROFESSOR));

        mockMvc.perform(post("/primeiro-acesso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"prof@escola.com","senhaProvisoria":"52998224725",
                                 "novaSenha":"nova-senha-forte"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(false));
    }

    @Test
    void primeiroAcessoComSenhaProvisoriaExpiradaResponde401() throws Exception {
        when(authService.definirSenhaDefinitiva(anyString(), anyString(), anyString()))
                .thenThrow(new SenhaProvisoriaExpiradaException());

        mockMvc.perform(post("/primeiro-acesso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"prof@escola.com","senhaProvisoria":"52998224725",
                                 "novaSenha":"nova-senha-forte"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(new SenhaProvisoriaExpiradaException().getMessage()));
    }

    @Test
    void primeiroAcessoSemNovaSenhaResponde400() throws Exception {
        mockMvc.perform(post("/primeiro-acesso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"prof@escola.com","senhaProvisoria":"52998224725"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
