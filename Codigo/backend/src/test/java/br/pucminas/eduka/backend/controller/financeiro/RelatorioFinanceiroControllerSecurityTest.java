package br.pucminas.eduka.backend.controller.financeiro;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.dto.comum.ArquivoCsv;
import br.pucminas.eduka.backend.dto.comum.ArquivoPlanilha;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Forma;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Inadimplencia;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Linha;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Mes;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Resumo;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Total;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.financeiro.RelatorioFinanceiroService;

/**
 * Quem ve os relatorios e o que a rota aceita: filter chain e
 * {@code @PreAuthorize} reais; o servico e mock (as contas estao em
 * RelatorioFinanceiroBancoRealTest).
 */
@WebMvcTest(value = RelatorioFinanceiroController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173")
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class RelatorioFinanceiroControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final LocalDate DE = LocalDate.of(2026, 8, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 9, 30);
    private static final String RELATORIO = "/relatorios/financeiro?de=2026-08-01&ate=2026-09-30";
    private static final String EXPORTAR = "/relatorios/financeiro/exportar?de=2026-08-01&ate=2026-09-30";
    private static final String PLANILHA = "/relatorios/financeiro/planilha?de=2026-08-01&ate=2026-09-30";
    private static final List<String> SEM_ACESSO = List.of("SECRETARIA", "PROFESSOR", "ALUNO", "SUPER_ADMIN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private RelatorioFinanceiroService relatorioFinanceiroService;

    private MockHttpServletRequestBuilder como(String url, String perfil) {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), perfil,
                "SUPER_ADMIN".equals(perfil) ? null : ESCOLA);
        return get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static ArquivoCsv arquivo() {
        return new ArquivoCsv("relatorio-financeiro-2026-08-01-a-2026-09-30.csv",
                "Cobranças\r\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(RELATORIO)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(EXPORTAR + "&conjuntos=DESPESAS")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PLANILHA)).andExpect(status().isUnauthorized());
    }

    @Test
    void masterEFinanceiroVeemORelatorio() throws Exception {
        when(relatorioFinanceiroService.gerar(DE, ATE)).thenReturn(new RelatorioFinanceiroResponse(DE, ATE,
                new Total(new BigDecimal("300.00"), 2),
                new Total(new BigDecimal("20.00"), 1),
                new Total(new BigDecimal("150.00"), 2),
                new Total(new BigDecimal("100.00"), 1),
                new BigDecimal("200.00"),
                new Inadimplencia(new BigDecimal("50.00"), 1, new BigDecimal("14.3")),
                List.of(new Mes(YearMonth.of(2026, 8), new BigDecimal("300.00"), new BigDecimal("100.00"),
                        new BigDecimal("200.00"))),
                List.of(new Forma(FormaPagamento.PIX, new BigDecimal("300.00"), 2, new BigDecimal("100.0"))),
                new Resumo(
                        List.of(new Linha("Ensino Medio", new BigDecimal("350.00"), new BigDecimal("300.00"),
                                new BigDecimal("50.00"), new BigDecimal("14.3"))),
                        List.of(), List.of(),
                        new Linha(null, new BigDecimal("350.00"), new BigDecimal("300.00"), new BigDecimal("50.00"),
                                new BigDecimal("14.3")))));

        for (String perfil : List.of("MASTER", "FINANCEIRO")) {
            mockMvc.perform(como(RELATORIO, perfil))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.de").value("2026-08-01"))
                    .andExpect(jsonPath("$.recebido.valor").value(300.00))
                    .andExpect(jsonPath("$.estornado.valor").value(20.00))
                    .andExpect(jsonPath("$.despesas.valor").value(150.00))
                    .andExpect(jsonPath("$.despesasPagas.valor").value(100.00))
                    .andExpect(jsonPath("$.saldo").value(200.00))
                    .andExpect(jsonPath("$.inadimplencia.percentual").value(14.3))
                    .andExpect(jsonPath("$.receitasEDespesas[0].mes").value("2026-08"))
                    .andExpect(jsonPath("$.formasPagamento[0].forma").value("PIX"))
                    .andExpect(jsonPath("$.resumo.porCurso[0].grupo").value("Ensino Medio"))
                    .andExpect(jsonPath("$.resumo.total.emAberto").value(50.00));
        }
    }

    @Test
    void outrosPerfisNaoChegamAoServico() throws Exception {
        // SECRETARIA entra no modulo /relatorios (RotasPorPerfil), mas nao nos financeiros.
        for (String perfil : SEM_ACESSO) {
            mockMvc.perform(como(RELATORIO, perfil)).andExpect(status().isForbidden());
            mockMvc.perform(como(EXPORTAR + "&conjuntos=COBRANCAS", perfil)).andExpect(status().isForbidden());
            mockMvc.perform(como(PLANILHA, perfil)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(relatorioFinanceiroService);
    }

    @Test
    void periodoInvalidoResponde400() throws Exception {
        when(relatorioFinanceiroService.gerar(ATE, DE))
                .thenThrow(new IllegalArgumentException("de nao pode ser depois de ate"));

        mockMvc.perform(como("/relatorios/financeiro?de=2026-09-30&ate=2026-08-01", "FINANCEIRO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("de nao pode ser depois de ate"));
        // Sem o formato fixo, o Spring leria a data pelo idioma do navegador.
        mockMvc.perform(como("/relatorios/financeiro?de=01/08/2026&ate=2026-09-30", "FINANCEIRO")
                .locale(Locale.of("pt", "BR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(como("/relatorios/financeiro?de=2026-08-01", "FINANCEIRO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportarDevolveUmAnexoCsvComOPeriodoNoNomeESemCache() throws Exception {
        when(relatorioFinanceiroService.exportar(DE, ATE, EnumSet.of(ArquivoCsv.Conjunto.COBRANCAS,
                ArquivoCsv.Conjunto.DESPESAS), ArquivoCsv.Separador.VIRGULA)).thenReturn(arquivo());

        mockMvc.perform(como(EXPORTAR + "&conjuntos=COBRANCAS,DESPESAS&separador=VIRGULA", "FINANCEIRO"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"relatorio-financeiro-2026-08-01-a-2026-09-30.csv\"")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(arquivo().conteudo()));
    }

    @Test
    void separadorPadraoEPontoEVirgula() throws Exception {
        when(relatorioFinanceiroService.exportar(DE, ATE, Set.of(ArquivoCsv.Conjunto.PAGAMENTOS),
                ArquivoCsv.Separador.PONTO_E_VIRGULA)).thenReturn(arquivo());

        mockMvc.perform(como(EXPORTAR + "&conjuntos=PAGAMENTOS", "MASTER")).andExpect(status().isOk());
    }

    @Test
    void exportarRecusaConjuntoOuSeparadorInvalidoSemGerarArquivo() throws Exception {
        mockMvc.perform(como(EXPORTAR, "FINANCEIRO")).andExpect(status().isBadRequest());
        mockMvc.perform(como(EXPORTAR + "&conjuntos=ALUNOS", "FINANCEIRO")).andExpect(status().isBadRequest());
        mockMvc.perform(como(EXPORTAR + "&conjuntos=COBRANCAS&separador=TAB", "FINANCEIRO"))
                .andExpect(status().isBadRequest());

        verify(relatorioFinanceiroService, never()).exportar(any(), any(), any(), any());
    }

    @Test
    void planilhaVemComoAnexoXlsxComONomeDaEscolaESemCache() throws Exception {
        byte[] conteudo = { 'P', 'K', 3, 4 };
        when(relatorioFinanceiroService.exportarPlanilha(DE, ATE)).thenReturn(new ArquivoPlanilha(
                "relatorio-financeiro-colegio-sao-jose-2026-08-01-a-2026-09-30.xlsx", conteudo));

        for (String perfil : List.of("MASTER", "FINANCEIRO")) {
            mockMvc.perform(como(PLANILHA, perfil))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(
                            "attachment; filename=\"relatorio-financeiro-colegio-sao-jose-2026-08-01-a-2026-09-30.xlsx\"")))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(content().bytes(conteudo));
        }
    }

    @Test
    void planilhaComPeriodoInvalidoResponde400() throws Exception {
        when(relatorioFinanceiroService.exportarPlanilha(ATE, DE))
                .thenThrow(new IllegalArgumentException("de nao pode ser depois de ate"));

        mockMvc.perform(como("/relatorios/financeiro/planilha?de=2026-09-30&ate=2026-08-01", "FINANCEIRO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("de nao pode ser depois de ate"));
        mockMvc.perform(como("/relatorios/financeiro/planilha?de=2026-08-01", "FINANCEIRO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oNomeDoArquivoFicaVisivelParaATelaEmOutraOrigem() throws Exception {
        when(relatorioFinanceiroService.exportar(any(), any(), any(), any())).thenReturn(arquivo());

        mockMvc.perform(como(EXPORTAR + "&conjuntos=DESPESAS", "FINANCEIRO").header(HttpHeaders.ORIGIN,
                "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION));
    }
}
