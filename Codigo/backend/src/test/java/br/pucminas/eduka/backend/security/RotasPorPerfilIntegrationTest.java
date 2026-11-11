package br.pucminas.eduka.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.controller.HealthController;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;

/**
 * Prova a matriz de {@link RotasPorPerfil} no filter chain real: para cada
 * modulo e cada perfil, quem esta na matriz passa e quem nao esta recebe 403,
 * antes de qualquer controller.
 *
 * <p>
 * Nenhum controller de modulo e carregado, entao quem passa pelo filtro
 * recebe 404 (rota sem endpoint). Assim o teste cobre inclusive os modulos
 * que ainda nao tem controller.
 * </p>
 */
@WebMvcTest(HealthController.class)
@Import({ JwtConfig.class, JwtService.class, ApiExceptionHandler.class })
class RotasPorPerfilIntegrationTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    // O HealthController (so ele nesta fatia) consulta o banco; aqui nao importa, so precisa de um DataSource.
    @MockitoBean
    private DataSource dataSource;

    private String bearer(PerfilUsuario perfil) {
        return "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), perfil.name(), ESCOLA);
    }

    static Stream<Arguments> modulosPorPerfil() {
        return Arrays.stream(RotasPorPerfil.values())
                .flatMap(modulo -> modulo.prefixos().stream()
                        .flatMap(prefixo -> Arrays.stream(PerfilUsuario.values())
                                .map(perfil -> Arguments.of(modulo, prefixo, perfil))));
    }

    @ParameterizedTest(name = "{0} {1} como {2}")
    @MethodSource("modulosPorPerfil")
    void subrotaDoModuloRespeitaMatriz(RotasPorPerfil modulo, String prefixo, PerfilUsuario perfil)
            throws Exception {
        var resultado = mockMvc.perform(get(prefixo + "/qualquer/subrota")
                .header(HttpHeaders.AUTHORIZATION, bearer(perfil)));

        if (modulo.perfis().contains(perfil)) {
            resultado.andExpect(status().isNotFound());
        } else {
            resultado.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("Acesso negado"));
        }
    }

    @ParameterizedTest
    @EnumSource(RotasPorPerfil.class)
    void raizDoModuloTambemEhProtegida(RotasPorPerfil modulo) throws Exception {
        String raiz = modulo.prefixos().getFirst();
        var semAcesso = Arrays.stream(PerfilUsuario.values())
                .filter(p -> !modulo.perfis().contains(p))
                .findFirst();

        if (semAcesso.isEmpty()) {
            // Modulo aberto a todos os perfis (ex.: PRIVACIDADE): a protecao que resta e exigir o token.
            mockMvc.perform(get(raiz)).andExpect(status().isUnauthorized());
            return;
        }
        mockMvc.perform(get(raiz).header(HttpHeaders.AUTHORIZATION, bearer(semAcesso.get())))
                .andExpect(status().isForbidden());
    }

    @Test
    void matrizValeParaQualquerMetodoHttp() throws Exception {
        mockMvc.perform(post("/cobrancas").header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.SECRETARIA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void moduloSemTokenResponde401() throws Exception {
        mockMvc.perform(get("/alunos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prefixoParecidoNaoHerdaRegraDoModulo() throws Exception {
        // "/alunosx" nao e "/alunos/**": cai em anyRequest().authenticated().
        mockMvc.perform(get("/alunosx").header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.ALUNO)))
                .andExpect(status().isNotFound());
    }
}
