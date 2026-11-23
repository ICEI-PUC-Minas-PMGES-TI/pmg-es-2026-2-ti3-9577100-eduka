package br.pucminas.eduka.backend.controller.acesso;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
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

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.dto.acesso.LoginResponse;
import br.pucminas.eduka.backend.dto.acesso.VerificacaoLoginResponse;
import br.pucminas.eduka.backend.exception.acesso.CodigoVerificacaoExpiradoException;
import br.pucminas.eduka.backend.exception.acesso.CodigoVerificacaoIncorretoException;
import br.pucminas.eduka.backend.exception.acesso.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.exception.acesso.ReenvioCodigoIndisponivelException;
import br.pucminas.eduka.backend.exception.acesso.SenhaProvisoriaExpiradaException;
import br.pucminas.eduka.backend.exception.acesso.SessaoExpiradaException;
import br.pucminas.eduka.backend.exception.acesso.VerificacaoLoginEncerradaException;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.service.acesso.AuthService;

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
                        PerfilUsuario.SECRETARIA, null));

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
                        PerfilUsuario.PROFESSOR, null));

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
    void loginComVerificacaoPendenteRespondeSemTokens() throws Exception {
        UUID verificacaoId = UUID.randomUUID();
        when(authService.login("usuario@escola.com", "senha-correta"))
                .thenReturn(LoginResponse.verificacaoPendente(UUID.randomUUID(), PerfilUsuario.SECRETARIA,
                        new VerificacaoLoginResponse(verificacaoId, 300, 60)));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"usuario@escola.com","senha":"senha-correta"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificacao.id").value(verificacaoId.toString()))
                .andExpect(jsonPath("$.verificacao.validadeSegundos").value(300))
                .andExpect(jsonPath("$.verificacao.reenvioSegundos").value(60))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void confirmarCodigoEhPublicoEDevolveTokens() throws Exception {
        UUID verificacaoId = UUID.randomUUID();
        when(authService.confirmarCodigo(verificacaoId, "123456"))
                .thenReturn(LoginResponse.autenticado("access-token", "refresh-token", UUID.randomUUID(),
                        PerfilUsuario.SECRETARIA, null));

        mockMvc.perform(post("/login/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificacaoId":"%s","codigo":"123456"}
                                """.formatted(verificacaoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void confirmarCodigoForaDoFormatoResponde400SemChegarAoServico() throws Exception {
        mockMvc.perform(post("/login/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificacaoId":"%s","codigo":"12345a"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void cadaDesfechoDoCodigoTemSeuStatus() throws Exception {
        String corpo = """
                {"verificacaoId":"%s","codigo":"123456"}
                """.formatted(UUID.randomUUID());

        when(authService.confirmarCodigo(any(), anyString())).thenThrow(new CodigoVerificacaoIncorretoException());
        mockMvc.perform(post("/login/codigo").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());

        reset(authService);
        when(authService.confirmarCodigo(any(), anyString())).thenThrow(new CodigoVerificacaoExpiradoException());
        mockMvc.perform(post("/login/codigo").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isGone());

        reset(authService);
        when(authService.confirmarCodigo(any(), anyString())).thenThrow(new VerificacaoLoginEncerradaException());
        mockMvc.perform(post("/login/codigo").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void reenvioEhPublicoEDevolveOsNovosPrazos() throws Exception {
        UUID verificacaoId = UUID.randomUUID();
        when(authService.reenviarCodigo(verificacaoId))
                .thenReturn(new VerificacaoLoginResponse(verificacaoId, 300, 60));

        mockMvc.perform(post("/login/codigo/reenvio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificacaoId":"%s"}
                                """.formatted(verificacaoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(verificacaoId.toString()))
                .andExpect(jsonPath("$.validadeSegundos").value(300));
    }

    @Test
    void reenvioCedoDemaisResponde429() throws Exception {
        when(authService.reenviarCodigo(any()))
                .thenThrow(new ReenvioCodigoIndisponivelException("Aguarde 30 segundos para pedir um novo codigo"));

        mockMvc.perform(post("/login/codigo/reenvio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificacaoId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void renovacaoEhPublicaEDevolveNovosTokens() throws Exception {
        when(authService.renovarSessao("refresh-antigo"))
                .thenReturn(LoginResponse.autenticado("access-novo", "refresh-novo", UUID.randomUUID(),
                        PerfilUsuario.FINANCEIRO, null));

        mockMvc.perform(post("/login/renovacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-antigo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-novo"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-novo"));
    }

    @Test
    void renovacaoComSessaoExpiradaResponde401ComCodigo() throws Exception {
        when(authService.renovarSessao(anyString())).thenThrow(new SessaoExpiradaException());

        mockMvc.perform(post("/login/renovacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"vencido"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_EXPIRADA"));
    }

    @Test
    void renovacaoSemRefreshTokenResponde400() throws Exception {
        mockMvc.perform(post("/login/renovacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
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
