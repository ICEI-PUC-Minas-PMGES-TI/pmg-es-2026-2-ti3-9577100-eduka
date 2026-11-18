package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.financeiro.StatusEventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * O webhook do Asaas de ponta a ponta: a requisicao HTTP passa pela seguranca,
 * pelo adaptador de verdade (que confere o token e le o JSON do Asaas) e chega
 * ao banco. Aviso repetido, inclusive ao mesmo tempo, registra o pagamento
 * uma vez so; token errado nao grava nada.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "asaas.webhook-token=" + WebhookBancoRealTest.TOKEN)
class WebhookBancoRealTest extends BancoRealTest {

    static final String TOKEN = "token-do-webhook-de-teste";

    @Autowired
    private MockMvc mockMvc;

    private UUID escola;
    private String contaDaEscola;
    private UUID cobranca;
    private String idNoAsaas;

    @BeforeEach
    void cobrancaEmitidaNoAsaas() {
        escola = dados.escola();
        contaDaEscola = dados.contaAprovada(escola);
        idNoAsaas = "pay_" + DadosDeTeste.unico();
        cobranca = dados.cobrancaEmitida(escola, dados.aluno(escola), idNoAsaas, FormaPagamento.PIX);
    }

    /** Aviso como o Asaas manda, com a conta filha que gerou o evento. */
    private String aviso(String idEvento, String tipo, String idConta) {
        return """
                {"id":"%s","event":"%s","dateCreated":"%s 10:15:00","account":{"id":"%s"},
                 "payment":{"id":"%s","externalReference":"EDK-B-%s","status":"RECEIVED","billingType":"PIX",
                            "value":100.00,"netValue":99.01,"paymentDate":"%s","pixTransaction":"pix_%s"}}
                """.formatted(idEvento, tipo, LocalDate.now(), idConta, idNoAsaas, cobranca, LocalDate.now(),
                idEvento);
    }

    /** Aviso de estorno de {@code idNoAsaas}, com os {@code refunds} do Asaas (JSON). */
    private String avisoDeEstorno(String idEvento, String tipo, String refunds) {
        return """
                {"id":"%s","event":"%s","dateCreated":"2026-10-20 10:15:00","account":{"id":"%s"},
                 "payment":{"id":"%s","externalReference":"EDK-B-%s","status":"REFUNDED","billingType":"PIX",
                            "value":100.00,"refunds":%s}}
                """.formatted(idEvento, tipo, contaDaEscola, idNoAsaas, cobranca, refunds);
    }

    private static String refund(String valor) {
        return "{\"status\":\"DONE\",\"value\":" + valor + "}";
    }

    private int enviar(String token, String corpo) throws Exception {
        var requisicao = post("/webhooks/asaas").contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (token != null) {
            requisicao.header("Asaas-Access-Token", token);
        }
        return mockMvc.perform(requisicao).andReturn().getResponse().getStatus();
    }

    private static String idDeEvento() {
        return "evt_" + DadosDeTeste.unico();
    }

    // ---------- Token ----------

    @Test
    void tokenErradoOuAusenteResponde401ENaoGravaNada() throws Exception {
        String idEvento = idDeEvento();
        String corpo = aviso(idEvento, "PAYMENT_RECEIVED", contaDaEscola);

        assertThat(enviar("token-errado", corpo)).isEqualTo(401);
        assertThat(enviar(null, corpo)).isEqualTo(401);

        assertThat(dados.eventosGravados(idEvento)).isZero();
        assertThat(dados.pagamentosDaCobranca(cobranca)).isZero();
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PENDENTE);
    }

    // ---------- Estorno ----------

    @Test
    void estornosParciaisDescontamSoADiferencaEACobrancaContinuaPaga() throws Exception {
        enviar(TOKEN, aviso(idDeEvento(), "PAYMENT_RECEIVED", contaDaEscola));

        String primeiro = avisoDeEstorno(idDeEvento(), "PAYMENT_PARTIALLY_REFUNDED", "[" + refund("30.00") + "]");
        assertThat(enviar(TOKEN, primeiro)).isEqualTo(200);
        assertThat(enviar(TOKEN, primeiro)).isEqualTo(200);
        // O segundo aviso traz os dois estornos: so os 20 sao novos.
        assertThat(enviar(TOKEN, avisoDeEstorno(idDeEvento(), "PAYMENT_PARTIALLY_REFUNDED",
                "[" + refund("30.00") + "," + refund("20.00") + "]"))).isEqualTo(200);

        assertThat(dados.valorEstornado(cobranca)).isEqualByComparingTo("50.00");
        assertThat(dados.estornosDaCobranca(cobranca)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("30.00"));
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PAGA);
    }

    @Test
    void estornoTotalSemRefundsDevolveOPagoSemPassarDele() throws Exception {
        enviar(TOKEN, aviso(idDeEvento(), "PAYMENT_RECEIVED", contaDaEscola));

        assertThat(enviar(TOKEN, avisoDeEstorno(idDeEvento(), "PAYMENT_REFUNDED", "null"))).isEqualTo(200);
        // Valor acima do pago nao passa do pago: a constraint do banco recusaria.
        assertThat(enviar(TOKEN, avisoDeEstorno(idDeEvento(), "PAYMENT_REFUNDED", "[" + refund("500.00") + "]")))
                .isEqualTo(200);

        assertThat(dados.valorEstornado(cobranca)).isEqualByComparingTo("100.00");
        assertThat(dados.estornosDaCobranca(cobranca)).hasSize(1);
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PAGA);
    }

    @Test
    void estornoDeCobrancaSemPagamentoFicaSoNaAuditoria() throws Exception {
        String idEvento = idDeEvento();

        assertThat(enviar(TOKEN, avisoDeEstorno(idEvento, "PAYMENT_REFUNDED", "[" + refund("100.00") + "]")))
                .isEqualTo(200);

        assertThat(dados.pagamentosDaCobranca(cobranca)).isZero();
        assertThat(dados.estornosDaCobranca(cobranca)).isEmpty();
        assertThat(dados.statusDoEvento(idEvento)).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(dados.operacoesNoLog(cobranca)).containsExactly(OperacaoAuditoria.ESTORNAR);
    }

    @Test
    void doisAvisosDeEstornoAoMesmoTempoNaoDescontamEmDobro() throws Exception {
        enviar(TOKEN, aviso(idDeEvento(), "PAYMENT_RECEIVED", contaDaEscola));
        String refunds = "[" + refund("40.00") + "]";
        CyclicBarrier largada = new CyclicBarrier(2);

        ExecutorService asaas = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> envios = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String corpo = avisoDeEstorno(idDeEvento(), "PAYMENT_PARTIALLY_REFUNDED", refunds);
                envios.add(asaas.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return enviar(TOKEN, corpo);
                }));
            }
            for (Future<Integer> envio : envios) {
                assertThat(envio.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            asaas.shutdownNow();
        }

        assertThat(dados.valorEstornado(cobranca)).isEqualByComparingTo("40.00");
        assertThat(dados.estornosDaCobranca(cobranca)).hasSize(1);
    }

    // ---------- Aviso repetido ----------

    @Test
    void avisoPagaACobrancaEORepetidoNaoMudaNada() throws Exception {
        String idEvento = idDeEvento();
        String corpo = aviso(idEvento, "PAYMENT_RECEIVED", contaDaEscola);

        assertThat(enviar(TOKEN, corpo)).isEqualTo(200);
        assertThat(enviar(TOKEN, corpo)).isEqualTo(200);

        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PAGA);
        assertThat(dados.pagamentosDaCobranca(cobranca)).isEqualTo(1);
        assertThat(dados.eventosGravados(idEvento)).isEqualTo(1);
        assertThat(dados.statusDoEvento(idEvento)).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(dados.operacoesNoLog(cobranca)).containsExactly(OperacaoAuditoria.CONFIRMAR_PAGAMENTO);
    }

    @Test
    void mesmoAvisoChegandoAoMesmoTempoRegistraUmPagamentoSo() throws Exception {
        String idEvento = idDeEvento();
        String corpo = aviso(idEvento, "PAYMENT_RECEIVED", contaDaEscola);
        CyclicBarrier largada = new CyclicBarrier(2);

        List<Integer> respostas = new ArrayList<>();
        ExecutorService asaas = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> envios = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                envios.add(asaas.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return enviar(TOKEN, corpo);
                }));
            }
            for (Future<Integer> envio : envios) {
                respostas.add(envio.get(30, TimeUnit.SECONDS));
            }
        } finally {
            asaas.shutdownNow();
        }

        // O Asaas so reenvia o que nao recebeu 200: os dois precisam ser aceitos.
        assertThat(respostas).containsExactly(200, 200);
        assertThat(dados.pagamentosDaCobranca(cobranca)).isEqualTo(1);
        assertThat(dados.eventosGravados(idEvento)).isEqualTo(1);
        assertThat(dados.operacoesNoLog(cobranca)).containsExactly(OperacaoAuditoria.CONFIRMAR_PAGAMENTO);
    }

    @Test
    void confirmadoERecebidoDoMesmoPagamentoRegistramUmPagamentoSo() throws Exception {
        // O Asaas avisa duas vezes o mesmo PIX, com ids de evento diferentes.
        assertThat(enviar(TOKEN, aviso(idDeEvento(), "PAYMENT_CONFIRMED", contaDaEscola))).isEqualTo(200);
        assertThat(enviar(TOKEN, aviso(idDeEvento(), "PAYMENT_RECEIVED", contaDaEscola))).isEqualTo(200);

        assertThat(dados.pagamentosDaCobranca(cobranca)).isEqualTo(1);
        assertThat(dados.operacoesNoLog(cobranca)).containsExactly(OperacaoAuditoria.CONFIRMAR_PAGAMENTO);
    }

    // ---------- Pagamento depois do cancelamento ----------

    @Test
    void pagamentoDeCobrancaCanceladaERegistradoESinalizadoNaAuditoria() throws Exception {
        // A escola cancelou, mas o pagador ja tinha o PIX em maos e pagou.
        dados.cancelada(cobranca);
        String idEvento = idDeEvento();

        assertThat(enviar(TOKEN, aviso(idEvento, "PAYMENT_RECEIVED", contaDaEscola))).isEqualTo(200);

        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PAGA);
        assertThat(dados.pagamentosDaCobranca(cobranca)).isEqualTo(1);
        assertThat(dados.statusDoEvento(idEvento)).isEqualTo(StatusEventoProvedor.PROCESSADO);
        assertThat(dados.operacoesNoLog(cobranca)).containsExactly(OperacaoAuditoria.PAGO_APOS_CANCELAR);
    }

    // ---------- Isolamento entre escolas ----------

    @Test
    void avisoVindoDaContaDeOutraEscolaEIgnorado() throws Exception {
        String contaDeOutraEscola = dados.contaAprovada(dados.escola());
        String idEvento = idDeEvento();

        assertThat(enviar(TOKEN, aviso(idEvento, "PAYMENT_RECEIVED", contaDeOutraEscola))).isEqualTo(200);

        assertThat(dados.statusDoEvento(idEvento)).isEqualTo(StatusEventoProvedor.IGNORADO);
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(dados.pagamentosDaCobranca(cobranca)).isZero();
    }
}
