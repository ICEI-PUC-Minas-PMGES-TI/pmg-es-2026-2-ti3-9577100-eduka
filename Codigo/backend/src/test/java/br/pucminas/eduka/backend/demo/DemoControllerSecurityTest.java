package br.pucminas.eduka.backend.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.dto.acesso.LoginResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.exception.acesso.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.security.JwtConfig;

/** Rotas publicas no perfil demo, conferidas pelo proprio acesso; fora dele nao existem. */
class DemoControllerSecurityTest {

    private static final String ACESSO = """
            {"login":"edukasystem@gmail.com","senha":"senha-do-acesso"}
            """;
    private static final String ENTRAR = """
            {"login":"edukasystem@gmail.com","senha":"senha-do-acesso","conta":"helena.duarte@mock.eduka.com.br"}
            """;

    @Nested
    @WebMvcTest(DemoController.class)
    @ActiveProfiles("demo")
    @Import({ JwtConfig.class, ApiExceptionHandler.class })
    class NoPerfilDemo {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private DemoAcessoService demoAcessoService;

        @Test
        void listaAsContasSemToken() throws Exception {
            when(demoAcessoService.contas("edukasystem@gmail.com", "senha-do-acesso")).thenReturn(List.of(
                    new DemoAcessoService.Grupo("Colégio Ipê Amarelo", "", List.of(new DemoAcessoService.Conta(
                            PerfilUsuario.MASTER, "Master", "helena.duarte@mock.eduka.com.br")))));

            mockMvc.perform(post("/demo/contas").contentType(MediaType.APPLICATION_JSON).content(ACESSO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].contas[0].perfil").value("MASTER"));
        }

        @Test
        void entraSemToken() throws Exception {
            when(demoAcessoService.entrar(any(), any(), any())).thenReturn(
                    LoginResponse.autenticado("access", "refresh", UUID.randomUUID(), PerfilUsuario.MASTER));

            mockMvc.perform(post("/demo/entrar").contentType(MediaType.APPLICATION_JSON).content(ENTRAR))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access"));
        }

        @Test
        void acessoErradoE401() throws Exception {
            when(demoAcessoService.contas(any(), any())).thenThrow(new CredenciaisInvalidasException());

            mockMvc.perform(post("/demo/contas").contentType(MediaType.APPLICATION_JSON).content(ACESSO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void corpoIncompletoE400() throws Exception {
            mockMvc.perform(post("/demo/entrar").contentType(MediaType.APPLICATION_JSON).content(ACESSO))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(demoAcessoService);
        }
    }

    @Nested
    @WebMvcTest(DemoController.class)
    @Import({ JwtConfig.class, ApiExceptionHandler.class })
    class ForaDoPerfilDemo {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void rotasNaoExistem() throws Exception {
            mockMvc.perform(post("/demo/contas").contentType(MediaType.APPLICATION_JSON).content(ACESSO))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/demo/entrar").contentType(MediaType.APPLICATION_JSON).content(ENTRAR))
                    .andExpect(status().isNotFound());
        }
    }
}
