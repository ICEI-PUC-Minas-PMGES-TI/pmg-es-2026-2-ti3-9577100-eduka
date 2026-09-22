package br.pucminas.eduka.backend.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.exception.ApiExceptionHandler;

/**
 * Prova, com o filter chain real e tokens reais, o contrato de seguranca da
 * API: 401 sem credencial valida, 403 sem permissao, isolamento por escola,
 * CORS restrito e resposta de erro sem vazamento.
 */
@WebMvcTest(AutorizacaoTestController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class AutorizacaoIntegrationTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();
    private static final String ORIGEM_FRONT = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    private String accessToken(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder getCom(String url, String token) {
        return get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Nested
    class Autenticacao {

        @Test
        void semTokenRespondeProblemDetail401ComWwwAuthenticate() throws Exception {
            mockMvc.perform(get("/teste/autenticado"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        void tokenMalformadoResponde401() throws Exception {
            mockMvc.perform(getCom("/teste/autenticado", "nao-e-um-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void refreshTokenNaoAbreRotaProtegida() throws Exception {
            // Assinado com a mesma chave, valido, mas nao e access token.
            String refresh = jwtService.generateRefreshToken(UUID.randomUUID());

            mockMvc.perform(getCom("/teste/autenticado", refresh))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void accessTokenExpiradoResponde401() throws Exception {
            String expirado = tokenManual(claims -> claims
                    .issuedAt(Instant.now().minusSeconds(7200))
                    .expiresAt(Instant.now().minusSeconds(3600)));

            mockMvc.perform(getCom("/teste/autenticado", expirado))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void accessTokenSemPerfilResponde401() throws Exception {
            String semPerfil = tokenManual(claims -> claims
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(600))
                    .claim("token_type", "access"));

            mockMvc.perform(getCom("/teste/autenticado", semPerfil))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void accessTokenSemEscolaEhAceito() throws Exception {
            // Usuarios sem escola (hoje, so SUPER_ADMIN) tem token legitimo assim.
            String semEscola = tokenManual(claims -> claims
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(600))
                    .claim("token_type", "access")
                    .claim("perfil", "SUPER_ADMIN"));

            mockMvc.perform(getCom("/teste/autenticado", semEscola))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.perfil").value("SUPER_ADMIN"))
                    .andExpect(jsonPath("$.escolaId").doesNotExist());
        }

        @Test
        void accessTokenValidoExpoeUsuarioAutenticadoComoPrincipal() throws Exception {
            mockMvc.perform(getCom("/teste/autenticado", accessToken("PROFESSOR", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.perfil").value("PROFESSOR"))
                    .andExpect(jsonPath("$.escolaId").value(ESCOLA_A.toString()));
        }

        private String tokenManual(java.util.function.UnaryOperator<JwtClaimsSet.Builder> customizacao) {
            JwtClaimsSet.Builder builder = JwtClaimsSet.builder().subject(UUID.randomUUID().toString());
            JwtClaimsSet claims = customizacao.apply(builder).build();
            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }
    }

    @Nested
    class AutorizacaoPorPerfil {

        @Test
        void perfilSemPermissaoRecebe403ComProblemDetail() throws Exception {
            mockMvc.perform(getCom("/teste/secretaria", accessToken("ALUNO", ESCOLA_A)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        void perfilExigidoRecebe200() throws Exception {
            mockMvc.perform(getCom("/teste/secretaria", accessToken("SECRETARIA", ESCOLA_A)))
                    .andExpect(status().isOk());
        }

        @Test
        void hasAnyRoleAceitaQualquerUmDosPerfis() throws Exception {
            mockMvc.perform(getCom("/teste/gestao", accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isOk());
            mockMvc.perform(getCom("/teste/gestao", accessToken("SUPER_ADMIN", ESCOLA_A)))
                    .andExpect(status().isOk());
            mockMvc.perform(getCom("/teste/gestao", accessToken("FINANCEIRO", ESCOLA_A)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void superAdminNaoPassaEmRoleQueNaoTem() throws Exception {
            // SUPER_ADMIN ignora isolamento de escola, nao autorizacao por perfil.
            mockMvc.perform(getCom("/teste/secretaria", accessToken("SUPER_ADMIN", ESCOLA_A)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class IsolamentoEntreEscolas {

        @Test
        void usuarioAcessaRecursoDaPropriaEscola() throws Exception {
            mockMvc.perform(getCom("/teste/escolas/" + ESCOLA_A + "/recurso", accessToken("SECRETARIA", ESCOLA_A)))
                    .andExpect(status().isOk());
        }

        @Test
        void usuarioNaoAcessaRecursoDeOutraEscola() throws Exception {
            mockMvc.perform(getCom("/teste/escolas/" + ESCOLA_B + "/recurso", accessToken("SECRETARIA", ESCOLA_A)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("Acesso negado"));
        }

        @Test
        void masterTambemFicaPresoNaPropriaEscola() throws Exception {
            mockMvc.perform(getCom("/teste/escolas/" + ESCOLA_B + "/recurso", accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void superAdminAcessaQualquerEscola() throws Exception {
            mockMvc.perform(getCom("/teste/escolas/" + ESCOLA_B + "/recurso", accessToken("SUPER_ADMIN", ESCOLA_A)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Cors {

        @Test
        void preflightDaOrigemDoFrontEhAceito() throws Exception {
            mockMvc.perform(options("/teste/autenticado")
                    .header(HttpHeaders.ORIGIN, ORIGEM_FRONT)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_FRONT))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")));
        }

        @Test
        void preflightDeOrigemDesconhecidaEhRecusado() throws Exception {
            mockMvc.perform(options("/teste/autenticado")
                    .header(HttpHeaders.ORIGIN, "https://site-malicioso.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void respostaParaOrigemDesconhecidaNaoCarregaAllowOrigin() throws Exception {
            mockMvc.perform(getCom("/teste/autenticado", accessToken("ALUNO", ESCOLA_A))
                    .header(HttpHeaders.ORIGIN, "https://site-malicioso.example"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Nested
    class RespostaDeErro {

        @Test
        void erroInternoNaoVazaMensagemNemClasse() throws Exception {
            mockMvc.perform(getCom("/teste/erro", accessToken("ALUNO", ESCOLA_A)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Erro interno"))
                    .andExpect(content().string(not(containsString("detalhe interno"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))));
        }
    }
}
