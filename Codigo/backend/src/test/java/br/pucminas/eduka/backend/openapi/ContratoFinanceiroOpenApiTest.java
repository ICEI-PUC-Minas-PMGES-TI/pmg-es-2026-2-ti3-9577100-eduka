package br.pucminas.eduka.backend.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import br.pucminas.eduka.backend.integracao.BancoRealTest;

/**
 * O contrato OpenAPI das rotas do financeiro, como o springdoc gera de
 * verdade: status de sucesso certos, os erros com o esquema {@code Erro} e os
 * codigos que as telas tratam. Pega rota nova sem 401/403 documentado e
 * resposta que voltou a ser {@code *}{@code /*}.
 */
class ContratoFinanceiroOpenApiTest extends BancoRealTest {

    /** Rotas de cobrancas, pagamentos, boletos e despesas (issue #218). */
    private static final List<String> ROTAS_DO_FINANCEIRO = List.of("/cobrancas", "/alunos/{alunoId}/financeiro",
            "/portal/aluno/cobrancas", "/despesas", "/categorias-despesa", "/config-pagamento",
            "/escolas/{escolaId}/faturas", "/dashboard/financeiro", "/relatorios/financeiro", "/webhooks/asaas");

    private static final String ERRO = "#/components/schemas/" + OpenApiConfig.ERRO;

    @Autowired
    private OpenApiWebMvcResource openApi;

    private DocumentContext contrato;

    @BeforeEach
    void gerarContrato() throws Exception {
        byte[] json = openApi.openapiJson(new MockHttpServletRequest(), "/v3/api-docs", Locale.ROOT);
        contrato = JsonPath.parse(new String(json, StandardCharsets.UTF_8));
    }

    private Map<String, Map<String, Object>> respostas(String metodo, String rota) {
        return contrato.read("$.paths['" + rota + "']." + metodo + ".responses");
    }

    private String descricao(String metodo, String rota, String status) {
        return contrato.read("$.paths['" + rota + "']." + metodo + ".responses['" + status + "'].description");
    }

    private static boolean doFinanceiro(String rota) {
        return ROTAS_DO_FINANCEIRO.stream().anyMatch(rota::startsWith);
    }

    @Test
    void todaRotaDoFinanceiroDocumentaTokenPerfilEOFormatoDoErro() {
        Map<String, Map<String, Object>> rotas = contrato.read("$.paths");
        List<String> financeiras = rotas.keySet().stream().filter(ContratoFinanceiroOpenApiTest::doFinanceiro).toList();
        assertThat(financeiras).hasSizeGreaterThanOrEqualTo(25);

        for (String rota : financeiras) {
            for (String metodo : rotas.get(rota).keySet()) {
                Map<String, Map<String, Object>> respostas = respostas(metodo, rota);
                String operacao = metodo.toUpperCase() + " " + rota;

                assertThat(respostas.keySet()).as(operacao + " sem resposta de sucesso")
                        .anyMatch(status -> status.startsWith("2"));
                assertThat(respostas).as(operacao).containsKey("401");
                if (!rota.equals("/webhooks/asaas")) {
                    assertThat(respostas).as(operacao + " (rota publica nao tem perfil)").containsKey("403");
                }
                respostas.forEach((status, resposta) -> {
                    String conteudo = String.valueOf(resposta.get("content"));
                    assertThat(conteudo).as(operacao + " " + status).doesNotContain("*/*");
                    if (status.startsWith("4") || status.startsWith("5")) {
                        assertThat(conteudo).as(operacao + " " + status).contains("application/problem+json", ERRO);
                    }
                });
            }
        }
    }

    @Test
    void nenhumaRotaDaApiFicaSemRespostaDeSucesso() {
        Map<String, Map<String, Object>> rotas = contrato.read("$.paths");

        rotas.forEach((rota, metodos) -> metodos.keySet().forEach(metodo -> assertThat(
                respostas(metodo, rota).keySet()).as(metodo.toUpperCase() + " " + rota)
                .anyMatch(status -> status.startsWith("2"))));
    }

    @Test
    void cadastrosRespondem201ComOQueFoiCriado() {
        for (String rota : List.of("/cobrancas", "/despesas", "/categorias-despesa")) {
            Map<String, Map<String, Object>> respostas = respostas("post", rota);

            assertThat(respostas).as(rota).containsKey("201").doesNotContainKey("200");
            assertThat(String.valueOf(respostas.get("201").get("content"))).as(rota).contains("application/json",
                    "$ref");
        }
    }

    @Test
    void pagarDocumentaOProcessandoEOsCodigosDeRecusa() {
        String pagar = "/portal/aluno/cobrancas/{id}/pagar";
        Map<String, Map<String, Object>> respostas = respostas("post", pagar);

        assertThat(respostas).containsKeys("200", "202", "400", "404", "409", "503");
        assertThat(String.valueOf(respostas.get("200").get("content"))).contains("DadosPagamentoResponse");
        assertThat(String.valueOf(respostas.get("202").get("content"))).contains("DadosPagamentoResponse");
        assertThat(descricao("post", pagar, "409")).contains("COBRANCA_NAO_PAGAVEL", "RECEBIMENTOS_INDISPONIVEIS",
                "PAGAMENTO_EM_PROCESSAMENTO");
        assertThat(descricao("post", pagar, "400")).contains("FORMA_INVALIDA", "PAGADOR_SEM_CPF", "PROVEDOR_RECUSOU");
    }

    @Test
    void boletoEUmPdf() {
        Map<String, Map<String, Object>> respostas = respostas("get", "/portal/aluno/cobrancas/{id}/boleto");

        assertThat(String.valueOf(respostas.get("200").get("content"))).contains("application/pdf");
        assertThat(descricao("get", "/portal/aluno/cobrancas/{id}/boleto", "409")).contains("BOLETO_NAO_GERADO");
    }

    @Test
    void exportarRelatorioEUmCsv() {
        Map<String, Map<String, Object>> respostas = respostas("get", "/relatorios/financeiro/exportar");

        assertThat(String.valueOf(respostas.get("200").get("content"))).contains("text/csv");
        assertThat(respostas).containsKeys("400", "401", "403");
    }

    @Test
    void cancelarCobrancaListaOsConflitos() {
        String cancelar = "/cobrancas/{id}/cancelar";

        assertThat(descricao("patch", cancelar, "409")).contains("COBRANCA_JA_PAGA", "PAGAMENTO_EM_PROCESSAMENTO");
        assertThat(respostas("patch", cancelar)).containsKeys("200", "400", "404", "503");
    }

    @Test
    void esquemaDoErroTrazOCodigo() {
        Map<String, Object> propriedades = contrato.read("$.components.schemas." + OpenApiConfig.ERRO + ".properties");

        assertThat(propriedades).containsKeys("status", "title", "detail", "codigo");
    }
}
