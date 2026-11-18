package br.pucminas.eduka.backend.controller.dashboard;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.dto.dashboard.DashboardEscolaResponse;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse;
import br.pucminas.eduka.backend.dto.dashboard.DashboardSecretariaResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.dashboard.DashboardService;
import br.pucminas.eduka.backend.service.financeiro.DashboardFinanceiroService;

/**
 * Quem ve cada dashboard: filter chain e {@code @PreAuthorize} reais; o
 * servico e mock (as contas estao em DashboardServiceTest).
 */
@WebMvcTest(DashboardController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class DashboardControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private DashboardFinanceiroService dashboardFinanceiroService;

    private MockHttpServletRequestBuilder como(String url, String perfil) {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), perfil,
                "SUPER_ADMIN".equals(perfil) ? null : ESCOLA);
        return get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/dashboard/escola")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/dashboard/secretaria")).andExpect(status().isUnauthorized());
    }

    @Test
    void soMasterVeOResumoDaEscola() throws Exception {
        when(dashboardService.escola()).thenReturn(new DashboardEscolaResponse(
                new DashboardEscolaResponse.Pessoas(3, 2, 1, 1, 0, 1, 0),
                new DashboardEscolaResponse.Estrutura(1, 2, 2, 2, "2026/2"),
                new DashboardEscolaResponse.Ocupacao(10, 2, 20),
                new DashboardEscolaResponse.Desempenho(4, 6, 75)));

        mockMvc.perform(como("/dashboard/escola", "MASTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoas.alunos").value(3))
                .andExpect(jsonPath("$.pessoas.alunosSemTurma").value(1))
                .andExpect(jsonPath("$.estrutura.periodoLetivoAtual").value("2026/2"))
                .andExpect(jsonPath("$.desempenho.aproveitamento").value(75));

        for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO", "SUPER_ADMIN")) {
            mockMvc.perform(como("/dashboard/escola", perfil)).andExpect(status().isForbidden());
        }
    }

    @Test
    void masterESecretariaVeemOResumoDeMatriculas() throws Exception {
        when(dashboardService.secretaria()).thenReturn(new DashboardSecretariaResponse(
                Map.of(StatusMatricula.ATIVA, 3L), 2, 1, new DashboardSecretariaResponse.Vagas(6, 3, 3, 1),
                List.of()));

        for (String perfil : List.of("MASTER", "SECRETARIA")) {
            mockMvc.perform(como("/dashboard/secretaria", perfil))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matriculasPorSituacao.ATIVA").value(3))
                    .andExpect(jsonPath("$.vagas.livres").value(3));
        }
        // FINANCEIRO entra no modulo /dashboard (RotasPorPerfil), mas nao neste resumo.
        for (String perfil : List.of("FINANCEIRO", "PROFESSOR", "ALUNO", "SUPER_ADMIN")) {
            mockMvc.perform(como("/dashboard/secretaria", perfil)).andExpect(status().isForbidden());
        }
    }

    @Test
    void masterEFinanceiroVeemOPainelFinanceiro() throws Exception {
        when(dashboardFinanceiroService.painel()).thenReturn(new DashboardFinanceiroResponse(
                new DashboardFinanceiroResponse.Total(new BigDecimal("850.50"), 2),
                new DashboardFinanceiroResponse.Total(new BigDecimal("1200.00"), 3),
                new DashboardFinanceiroResponse.Atraso(1, 1, new BigDecimal("300.00"), 25),
                new DashboardFinanceiroResponse.DespesasAPagar(new BigDecimal("90.00"), 1, LocalDate.of(2026, 10, 30)),
                List.of(new DashboardFinanceiroResponse.Mes(YearMonth.of(2026, 10), new BigDecimal("850.50"),
                        new BigDecimal("40.00"))),
                List.of(), List.of(), List.of()));

        for (String perfil : List.of("MASTER", "FINANCEIRO")) {
            mockMvc.perform(como("/dashboard/financeiro", perfil))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recebidoNoMes.valor").value(850.50))
                    .andExpect(jsonPath("$.recebidoNoMes.quantidade").value(2))
                    .andExpect(jsonPath("$.emAtraso.inadimplencia").value(25))
                    .andExpect(jsonPath("$.despesasAPagar.proximoVencimento").value("2026-10-30"))
                    .andExpect(jsonPath("$.ultimosMeses[0].mes").value("2026-10"));
        }
        for (String perfil : List.of("SECRETARIA", "PROFESSOR", "ALUNO", "SUPER_ADMIN")) {
            mockMvc.perform(como("/dashboard/financeiro", perfil)).andExpect(status().isForbidden());
        }
    }

    @Test
    void movimentoDoPeriodoSoParaMasterEFinanceiroEComDatasValidas() throws Exception {
        LocalDate de = LocalDate.of(2026, 8, 15);
        LocalDate ate = LocalDate.of(2026, 10, 20);
        when(dashboardFinanceiroService.movimento(de, ate)).thenReturn(List.of(
                new DashboardFinanceiroResponse.Mes(YearMonth.of(2026, 8), new BigDecimal("100.00"), BigDecimal.ZERO),
                new DashboardFinanceiroResponse.Mes(YearMonth.of(2026, 9), BigDecimal.ZERO, new BigDecimal("40.00")),
                new DashboardFinanceiroResponse.Mes(YearMonth.of(2026, 10), BigDecimal.ZERO, BigDecimal.ZERO)));
        String url = "/dashboard/financeiro/movimento?de=2026-08-15&ate=2026-10-20";

        for (String perfil : List.of("MASTER", "FINANCEIRO")) {
            mockMvc.perform(como(url, perfil))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].mes").value("2026-08"))
                    .andExpect(jsonPath("$[1].saiu").value(40.00));
        }
        for (String perfil : List.of("SECRETARIA", "PROFESSOR", "ALUNO", "SUPER_ADMIN")) {
            mockMvc.perform(como(url, perfil)).andExpect(status().isForbidden());
        }

        when(dashboardFinanceiroService.movimento(ate, de))
                .thenThrow(new IllegalArgumentException("de nao pode ser depois de ate"));
        mockMvc.perform(como("/dashboard/financeiro/movimento?de=2026-10-20&ate=2026-08-15", "FINANCEIRO"))
                .andExpect(status().isBadRequest());
        // Sem o formato fixo, o Spring leria a data pelo idioma do navegador.
        mockMvc.perform(como("/dashboard/financeiro/movimento?de=15/08/2026&ate=2026-10-20", "FINANCEIRO")
                .locale(Locale.of("pt", "BR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(como("/dashboard/financeiro/movimento?de=2026-08-15", "FINANCEIRO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void perfilSemPermissaoNaoChegaAoServico() throws Exception {
        mockMvc.perform(como("/dashboard/escola", "PROFESSOR")).andExpect(status().isForbidden());
        verify(dashboardService, never()).escola();
    }
}
