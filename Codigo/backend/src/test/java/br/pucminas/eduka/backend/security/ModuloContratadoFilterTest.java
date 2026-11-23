package br.pucminas.eduka.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.controller.HealthController;
import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.escola.ModuloEscola;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;

/**
 * Modulos contratados (S5-11) na cadeia de seguranca real: sem o modulo, a
 * rota dele e o perfil que so existe com ele dao 403
 * {@code MODULO_NAO_CONTRATADO}. Com o modulo, a requisicao segue e da 404
 * (so o HealthController existe nesta fatia).
 */
@WebMvcTest(HealthController.class)
@Import({ JwtConfig.class, JwtService.class, ApiExceptionHandler.class })
class ModuloContratadoFilterTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private EscolaRepository escolaRepository;

    @BeforeEach
    void escolaAtiva() {
        when(escolaRepository.situacao(ESCOLA)).thenReturn(Optional.empty());
    }

    private String bearer(PerfilUsuario perfil) {
        return "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), perfil.name(),
                perfil == PerfilUsuario.SUPER_ADMIN ? null : ESCOLA);
    }

    private void contratados(ModuloEscola... modulos) {
        when(escolaRepository.modulos(ESCOLA)).thenReturn(Arrays.stream(modulos).map(Enum::name).toList());
    }

    private static ModuloEscola[] todosMenos(ModuloEscola fora) {
        return Arrays.stream(ModuloEscola.values()).filter(m -> m != fora).toArray(ModuloEscola[]::new);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/cobrancas", "/cobrancas/123", "/despesas", "/relatorios/financeiro/resumo",
            "/dashboard/financeiro", "/alunos/" + "abc" + "/financeiro", "/config-pagamento/conta" })
    void semGestaoFinanceiraOMasterPerdeAsRotasDoFinanceiro(String rota) throws Exception {
        contratados(todosMenos(ModuloEscola.GESTAO_FINANCEIRA));

        mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.MASTER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NAO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("GESTAO_FINANCEIRA"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/alunos", "/turmas", "/config-pagamento/faturas", "/config-pagamento/assinatura",
            "/dashboard/escola" })
    void semGestaoFinanceiraOMasterContinuaNoRestoEPagaOEduka(String rota) throws Exception {
        contratados(todosMenos(ModuloEscola.GESTAO_FINANCEIRA));

        mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.MASTER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void financeiroNaoEntraEmNadaSemGestaoFinanceira() throws Exception {
        contratados(todosMenos(ModuloEscola.GESTAO_FINANCEIRA));

        mockMvc.perform(get("/alunos").header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.FINANCEIRO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.modulo").value("GESTAO_FINANCEIRA"));
    }

    @Test
    void alunoSemPortalDoAlunoEBarrado() throws Exception {
        contratados(todosMenos(ModuloEscola.PORTAL_ALUNO));

        mockMvc.perform(get("/portal/aluno/me").header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.ALUNO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.modulo").value("PORTAL_ALUNO"));
    }

    @Test
    void alunoSemGestaoFinanceiraPerdeSoAAbaFinanceira() throws Exception {
        contratados(todosMenos(ModuloEscola.GESTAO_FINANCEIRA));

        mockMvc.perform(get("/portal/aluno/boletim").header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.ALUNO)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/portal/aluno/financeiro")
                .header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.ALUNO)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/portal/aluno/cobrancas/1/boleto")
                .header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.ALUNO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void professorSemControleDeFrequenciaPerdeSoAChamada() throws Exception {
        contratados(todosMenos(ModuloEscola.CONTROLE_FREQUENCIA));

        mockMvc.perform(get("/portal/professor/aulas/chamada")
                .header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.PROFESSOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.modulo").value("CONTROLE_FREQUENCIA"));
        mockMvc.perform(get("/portal/professor/avaliacoes")
                .header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.PROFESSOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    void professorSemPortalDoProfessorEBarrado() throws Exception {
        contratados(todosMenos(ModuloEscola.PORTAL_PROFESSOR));

        mockMvc.perform(get("/turmas").header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.PROFESSOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.modulo").value("PORTAL_PROFESSOR"));
    }

    @Test
    void rotaSemModuloNaoConsultaOBanco() throws Exception {
        mockMvc.perform(get("/turmas").header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.SECRETARIA)))
                .andExpect(status().isNotFound());

        verify(escolaRepository, never()).modulos(any());
    }

    @Test
    void superAdminNaoTemEscolaEPassaDireto() throws Exception {
        mockMvc.perform(get("/escolas/qualquer").header(HttpHeaders.AUTHORIZATION, bearer(PerfilUsuario.SUPER_ADMIN)))
                .andExpect(status().isNotFound());

        verify(escolaRepository, never()).modulos(any());
    }

    @Test
    void todoPerfilComModuloProprioTemOModuloMapeado() {
        assertThat(RotasPorModulo.exigidoPeloPerfil(PerfilUsuario.FINANCEIRO)).contains(ModuloEscola.GESTAO_FINANCEIRA);
        assertThat(RotasPorModulo.exigidoPeloPerfil(PerfilUsuario.ALUNO)).contains(ModuloEscola.PORTAL_ALUNO);
        assertThat(RotasPorModulo.exigidoPeloPerfil(PerfilUsuario.PROFESSOR)).contains(ModuloEscola.PORTAL_PROFESSOR);
        for (PerfilUsuario perfil : List.of(PerfilUsuario.MASTER, PerfilUsuario.SECRETARIA, PerfilUsuario.SUPER_ADMIN)) {
            assertThat(RotasPorModulo.exigidoPeloPerfil(perfil)).isEmpty();
        }
    }
}
