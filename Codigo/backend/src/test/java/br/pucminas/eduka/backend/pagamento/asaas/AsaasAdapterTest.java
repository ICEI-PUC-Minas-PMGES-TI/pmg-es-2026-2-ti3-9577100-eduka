package br.pucminas.eduka.backend.pagamento.asaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;

import java.net.URI;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.pucminas.eduka.backend.pagamento.ProvedorIndisponivelException;
import br.pucminas.eduka.backend.pagamento.ProvedorNaoConfiguradoException;
import br.pucminas.eduka.backend.pagamento.ProvedorRecusouException;
import br.pucminas.eduka.backend.pagamento.asaas.dto.CobrancaAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.EventoAsaas;
import br.pucminas.eduka.backend.pagamento.tipos.BoletoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.ContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosBancarios;
import br.pucminas.eduka.backend.pagamento.tipos.DadosNovaConta;
import br.pucminas.eduka.backend.pagamento.tipos.DadosPagador;
import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.NovaCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.PixProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoCadastral;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.TipoContaBancaria;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEmpresa;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;

/** TST-A01 a A07: contrato HTTP com o Asaas, a partir das fixtures do Sandbox (NOVA-2). */
@ExtendWith(OutputCaptureExtension.class)
class AsaasAdapterTest {

    private static final String URL = "https://api-sandbox.asaas.com";
    private static final String CHAVE_PAI = "chave-ficticia-da-conta-pai";
    private static final String TOKEN_WEBHOOK = "token-do-webhook-com-mais-de-32-caracteres";
    private static final CredencialProvedor CREDENCIAL = new CredencialProvedor("chave-ficticia-da-escola");

    private static final AsaasProperties PROPERTIES = new AsaasProperties(URL, CHAVE_PAI,
            "https://tunel.example.com/webhooks/asaas", TOKEN_WEBHOOK, "falhas@example.com");

    private static final DadosNovaConta NOVA_CONTA = new DadosNovaConta("Escola Teste Eduka Ltda",
            "escola.teste@example.com", "11222333000181", "31987654321", TipoEmpresa.LIMITED,
            new BigDecimal("50000.00"), "Avenida Afonso Pena", "1000", "Sala 1", "Centro", "30130002",
            new DadosBancarios("341", "1234", "987654321", "7", TipoContaBancaria.CORRENTE));

    private MockRestServiceServer servidor;
    private AsaasAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = adapterCom(PROPERTIES);
    }

    private AsaasAdapter adapterCom(AsaasProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        return new AsaasAdapter(builder, properties);
    }

    private static String fixture(String nome) {
        try {
            return new ClassPathResource("asaas/" + nome).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String listaCom(String item) {
        return "{\"object\":\"list\",\"hasMore\":false,\"totalCount\":1,\"limit\":10,\"offset\":0,\"data\":["
                + item + "]}";
    }

    // ---- A01: metodo, caminho, header e corpo de cada operacao ----

    @Test
    void criarContaUsaAChaveDaContaPaiEEnviaOWebhook() {
        servidor.expect(requestTo(URL + "/v3/accounts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", CHAVE_PAI))
                .andExpect(header("User-Agent", "Eduka"))
                .andExpect(jsonPath("$.name").value("Escola Teste Eduka Ltda"))
                .andExpect(jsonPath("$.cpfCnpj").value("11222333000181"))
                .andExpect(jsonPath("$.mobilePhone").value("31987654321"))
                .andExpect(jsonPath("$.incomeValue").value(50000.00))
                .andExpect(jsonPath("$.companyType").value("LIMITED"))
                .andExpect(jsonPath("$.address").value("Avenida Afonso Pena"))
                .andExpect(jsonPath("$.addressNumber").value("1000"))
                .andExpect(jsonPath("$.complement").value("Sala 1"))
                .andExpect(jsonPath("$.province").value("Centro"))
                .andExpect(jsonPath("$.postalCode").value("30130002"))
                .andExpect(jsonPath("$.bankAccount").doesNotExist())
                .andExpect(jsonPath("$.webhooks[0].url").value("https://tunel.example.com/webhooks/asaas"))
                .andExpect(jsonPath("$.webhooks[0].email").value("falhas@example.com"))
                .andExpect(jsonPath("$.webhooks[0].authToken").value(TOKEN_WEBHOOK))
                .andExpect(jsonPath("$.webhooks[0].enabled").value(true))
                .andExpect(jsonPath("$.webhooks[0].interrupted").value(false))
                .andExpect(jsonPath("$.webhooks[0].sendType").value("SEQUENTIALLY"))
                .andExpect(jsonPath("$.webhooks[0].events.length()").value(AsaasAdapter.EVENTOS_WEBHOOK.size()))
                .andExpect(jsonPath("$.webhooks[0].events[0]").value("PAYMENT_CREATED"))
                .andRespond(withSuccess(fixture("conta-criada.json"), MediaType.APPLICATION_JSON));

        ContaProvedor conta = adapter.criarConta(NOVA_CONTA);

        servidor.verify();
        assertThat(conta.idContaExterna()).isEqualTo("3d134583-fe86-4aad-800f-6c6e63629478");
        assertThat(conta.walletId()).isEqualTo("0b604795-134a-4eca-bafa-7680bb9e05d8");
        // A chave vem em apiKey; accessToken e um objeto (achado A15).
        assertThat(conta.credencial().valor()).endsWith("CHAVE_FICTICIA_DA_SUBCONTA");
    }

    @Test
    void criarContaSemEmailDoWebhookNaoEnviaWebhook() {
        AsaasAdapter semEmail = adapterCom(new AsaasProperties(URL, CHAVE_PAI,
                "https://tunel.example.com/webhooks/asaas", TOKEN_WEBHOOK, ""));
        servidor.expect(requestTo(URL + "/v3/accounts"))
                .andExpect(jsonPath("$.webhooks").doesNotExist())
                .andRespond(withSuccess(fixture("conta-criada.json"), MediaType.APPLICATION_JSON));

        semEmail.criarConta(NOVA_CONTA);

        servidor.verify();
    }

    @Test
    void buscarContaPorCnpjNaoTrazCredencial() {
        servidor.expect(requestTo(URL + "/v3/accounts?cpfCnpj=11222333000181"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CHAVE_PAI))
                .andRespond(withSuccess(fixture("conta-busca-cnpj.json"), MediaType.APPLICATION_JSON));

        Optional<ContaProvedor> conta = adapter.buscarContaPorCnpj("11222333000181");

        assertThat(conta).get()
                .extracting(ContaProvedor::idContaExterna, ContaProvedor::credencial)
                .containsExactly("3d134583-fe86-4aad-800f-6c6e63629478", null);
    }

    @Test
    void buscarContaPorCnpjSemResultadoDevolveVazio() {
        servidor.expect(requestTo(URL + "/v3/accounts?cpfCnpj=11222333000181"))
                .andRespond(withSuccess(fixture("lista-cobrancas-vazia.json"), MediaType.APPLICATION_JSON));

        assertThat(adapter.buscarContaPorCnpj("11222333000181")).isEmpty();
    }

    @Test
    void consultarSituacaoContaUsaAChaveDaEscola() {
        servidor.expect(requestTo(URL + "/v3/myAccount/status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(fixture("conta-status.json"), MediaType.APPLICATION_JSON));

        SituacaoContaProvedor situacao = adapter.consultarSituacaoConta(CREDENCIAL);

        assertThat(situacao).isEqualTo(new SituacaoContaProvedor(SituacaoCadastral.APROVADO,
                SituacaoCadastral.APROVADO, SituacaoCadastral.APROVADO, SituacaoCadastral.APROVADO));
    }

    @Test
    void garantirChavePixCriaChaveAleatoriaQuandoAContaNaoTemNenhuma() {
        servidor.expect(requestTo(URL + "/v3/pix/addressKeys"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(fixture("lista-cobrancas-vazia.json"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/v3/pix/addressKeys"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andExpect(jsonPath("$.type").value("EVP"))
                .andRespond(withSuccess(fixture("pix-chave-criada.json"), MediaType.APPLICATION_JSON));

        adapter.garantirChavePix(CREDENCIAL);

        servidor.verify();
    }

    @Test
    void garantirChavePixNaoCriaOutraQuandoJaExisteChaveAtiva() {
        servidor.expect(requestTo(URL + "/v3/pix/addressKeys"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(listaCom(fixture("pix-chave-criada.json")), MediaType.APPLICATION_JSON));

        adapter.garantirChavePix(CREDENCIAL);

        servidor.verify();
    }

    @Test
    void garantirChavePixComExcessoDeConsultasEInconclusivo() {
        servidor.expect(requestTo(URL + "/v3/pix/addressKeys"))
                .andRespond(withSuccess(fixture("lista-cobrancas-vazia.json"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/v3/pix/addressKeys"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("erro-400-excesso-consultas.json")));

        assertThatThrownBy(() -> adapter.garantirChavePix(CREDENCIAL))
                .isInstanceOf(ProvedorIndisponivelException.class);
    }

    @Test
    void obterOuCriarClienteReaproveitaClienteExistente() {
        servidor.expect(requestTo(URL + "/v3/customers?externalReference=EDK-PES-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(listaCom(fixture("cliente-criado.json")), MediaType.APPLICATION_JSON));

        String id = adapter.obterOuCriarCliente(CREDENCIAL,
                new DadosPagador("EDK-PES-1", "Aluno Teste Eduka", "12345678909", "aluno.teste@example.com"));

        servidor.verify();
        assertThat(id).isEqualTo("cus_000009569905");
    }

    @Test
    void obterOuCriarClienteCriaComNotificacoesDoAsaasDesligadas() {
        servidor.expect(requestTo(URL + "/v3/customers?externalReference=EDK-PES-1"))
                .andRespond(withSuccess(fixture("lista-cobrancas-vazia.json"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/v3/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andExpect(jsonPath("$.name").value("Aluno Teste Eduka"))
                .andExpect(jsonPath("$.cpfCnpj").value("12345678909"))
                .andExpect(jsonPath("$.email").value("aluno.teste@example.com"))
                .andExpect(jsonPath("$.externalReference").value("EDK-PES-1"))
                .andExpect(jsonPath("$.notificationDisabled").value(true))
                .andRespond(withSuccess(fixture("cliente-criado.json"), MediaType.APPLICATION_JSON));

        String id = adapter.obterOuCriarCliente(CREDENCIAL,
                new DadosPagador("EDK-PES-1", "Aluno Teste Eduka", "12345678909", "aluno.teste@example.com"));

        servidor.verify();
        assertThat(id).isEqualTo("cus_000009569905");
    }

    @Test
    void criarCobrancaEnviaFormaValorVencimentoEReferencia() {
        servidor.expect(requestTo(URL + "/v3/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andExpect(jsonPath("$.customer").value("cus_000009569905"))
                .andExpect(jsonPath("$.billingType").value("PIX"))
                .andExpect(jsonPath("$.value").value(150.00))
                .andExpect(jsonPath("$.dueDate").value("2026-10-24"))
                .andExpect(jsonPath("$.description").value("Mensalidade teste"))
                .andExpect(jsonPath("$.externalReference").value("EDK-B-1"))
                .andRespond(withSuccess(fixture("cobranca-pix-criada.json"), MediaType.APPLICATION_JSON));

        CobrancaProvedor cobranca = adapter.criarCobranca(CREDENCIAL, new NovaCobrancaProvedor("cus_000009569905",
                FormaPagamento.PIX, new BigDecimal("150.00"), LocalDate.of(2026, 10, 24), "Mensalidade teste",
                "EDK-B-1"));

        servidor.verify();
        assertThat(cobranca.idCobrancaExterna()).isEqualTo("pay_1170f83b80iun8gy");
        assertThat(cobranca.status()).isEqualTo(StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO);
        assertThat(cobranca.forma()).isEqualTo(FormaPagamento.PIX);
        assertThat(cobranca.valorPago()).isNull();
    }

    @Test
    void buscarCobrancaPorReferenciaFiltraPelaReferenciaExterna() {
        servidor.expect(requestTo(URL + "/v3/payments?externalReference=EDK-B-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(listaCom(fixture("cobranca-pix-criada.json")), MediaType.APPLICATION_JSON));

        assertThat(adapter.buscarCobrancaPorReferencia(CREDENCIAL, "EDK-B-1")).get()
                .extracting(CobrancaProvedor::idCobrancaExterna).isEqualTo("pay_1170f83b80iun8gy");
    }

    @Test
    void buscarCobrancaPorReferenciaIgnoraRemovidas() {
        servidor.expect(requestTo(URL + "/v3/payments?externalReference=EDK-B-2"))
                .andRespond(withSuccess(listaCom(fixture("cobranca-removida.json")), MediaType.APPLICATION_JSON));

        assertThat(adapter.buscarCobrancaPorReferencia(CREDENCIAL, "EDK-B-2")).isEmpty();
    }

    @Test
    void consultarCobrancaRecebidaTrazDadosDoPagamento() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1170f83b80iun8gy"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(fixture("cobranca-recebida.json"), MediaType.APPLICATION_JSON));

        CobrancaProvedor cobranca = adapter.consultarCobranca(CREDENCIAL, "pay_1170f83b80iun8gy");

        assertThat(cobranca.status()).isEqualTo(StatusCobrancaProvedor.RECEBIDA);
        assertThat(cobranca.valorPago()).isEqualByComparingTo("150.00");
        assertThat(cobranca.valorLiquido()).isEqualByComparingTo("149.01");
        assertThat(cobranca.dataPagamento()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(cobranca.dataCredito()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(cobranca.idTransacao()).isEqualTo("ea75221a-ff3b-4460-9ec0-f4a5c7e3056f");
        assertThat(cobranca.urlReciboProvedor()).startsWith("https://sandbox.asaas.com/");
    }

    @Test
    void alterarFormaEVencimentoUsaPutNaMesmaCobranca() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1170f83b80iun8gy"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andExpect(jsonPath("$.billingType").value("BOLETO"))
                .andExpect(jsonPath("$.dueDate").value("2026-10-30"))
                .andRespond(withSuccess(fixture("cobranca-boleto-criada.json"), MediaType.APPLICATION_JSON));

        CobrancaProvedor cobranca = adapter.alterarFormaEVencimento(CREDENCIAL, "pay_1170f83b80iun8gy",
                FormaPagamento.BOLETO, LocalDate.of(2026, 10, 30));

        servidor.verify();
        assertThat(cobranca.forma()).isEqualTo(FormaPagamento.BOLETO);
    }

    @Test
    void removerCobrancaUsaDelete() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_zfqdvuxl0xciwdfb"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(fixture("cobranca-remover.json"), MediaType.APPLICATION_JSON));

        adapter.removerCobranca(CREDENCIAL, "pay_zfqdvuxl0xciwdfb");

        servidor.verify();
    }

    @Test
    void obterPixConverteQrCodeCopiaEColaEValidade() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1170f83b80iun8gy/pixQrCode"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(fixture("pix-qrcode.json"), MediaType.APPLICATION_JSON));

        PixProvedor pix = adapter.obterPix(CREDENCIAL, "pay_1170f83b80iun8gy");

        assertThat(pix.qrCodeBase64()).startsWith("iVBORw0KGgo");
        assertThat(pix.copiaECola()).startsWith("00020101");
        assertThat(pix.validade()).isEqualTo(OffsetDateTime.of(2027, 10, 24, 23, 59, 59, 0, ZoneOffset.ofHours(-3)));
    }

    @Test
    void obterLinhaDigitavelConverteOsCampos() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_zfqdvuxl0xciwdfb/identificationField"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(fixture("boleto-linha.json"), MediaType.APPLICATION_JSON));

        BoletoProvedor boleto = adapter.obterLinhaDigitavel(CREDENCIAL, "pay_zfqdvuxl0xciwdfb");

        assertThat(boleto).isEqualTo(new BoletoProvedor("46191110000000000000013638720014916090000020000",
                "46199160900000200001110000000000001363872001", "13638720"));
    }

    @Test
    void sandboxConfirmarPagamentoUsaOAtalhoDoSandbox() {
        servidor.expect(requestTo(URL + "/v3/sandbox/payment/pay_1170f83b80iun8gy/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess(fixture("cobranca-recebida.json"), MediaType.APPLICATION_JSON));

        adapter.sandboxConfirmarPagamento(CREDENCIAL, "pay_1170f83b80iun8gy");

        servidor.verify();
    }

    @Test
    void sandboxAprovarContaUsaAChaveDaEscola() {
        servidor.expect(requestTo(URL + "/v3/sandbox/myAccount/approve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess());

        adapter.sandboxAprovarConta(CREDENCIAL);

        servidor.verify();
    }

    // ---- A02: mapeamento de status e eventos ----

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "PENDING, false, AGUARDANDO_PAGAMENTO",
            "OVERDUE, false, VENCIDA",
            "CONFIRMED, false, CONFIRMADA",
            "RECEIVED, false, RECEBIDA",
            "RECEIVED_IN_CASH, false, RECEBIDA",
            "REFUNDED, false, OUTRO",
            "CHARGEBACK_REQUESTED, false, OUTRO",
            "AWAITING_RISK_ANALYSIS, false, OUTRO",
            "OVERDUE, true, REMOVIDA",
            "null, false, OUTRO"
    })
    void mapeiaStatusDaCobranca(String status, boolean removida, StatusCobrancaProvedor esperado) {
        CobrancaAsaas cobranca = new CobrancaAsaas("pay_1", "EDK-B-1", status, "PIX", removida, BigDecimal.TEN,
                BigDecimal.ONE, null, null, null, null, null, null, null, null);

        assertThat(AsaasAdapter.statusDaCobranca(cobranca)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "PENDING, PENDENTE",
            "AWAITING_APPROVAL, EM_ANALISE",
            "APPROVED, APROVADO",
            "REJECTED, REJEITADO",
            "null, null"
    })
    void mapeiaSituacaoDaConta(String situacao, SituacaoCadastral esperada) {
        assertThat(AsaasAdapter.situacao(situacao)).isEqualTo(esperada);
    }

    /** Nomes da documentacao de webhooks de cobranca do Asaas (RF22.2). */
    @ParameterizedTest
    @CsvSource({
            "PAYMENT_REFUNDED, ESTORNO",
            "PAYMENT_PARTIALLY_REFUNDED, ESTORNO",
            "PAYMENT_REFUND_IN_PROGRESS, NAO_TRATADO",
            "PAYMENT_REFUND_DENIED, NAO_TRATADO",
            "PAYMENT_CHARGEBACK_REQUESTED, CONTESTACAO",
            "PAYMENT_CHARGEBACK_DISPUTE, CONTESTACAO",
            "PAYMENT_AWAITING_CHARGEBACK_REVERSAL, CONTESTACAO"
    })
    void mapeiaEstornosEContestacoes(String evento, TipoEventoProvedor esperado) {
        assertThat(AsaasAdapter.tipoDoEvento(evento)).isEqualTo(esperado);
    }

    @Test
    void webhookDaContaFilhaAssinaOsEventosDeEstornoEContestacao() {
        assertThat(AsaasAdapter.EVENTOS_WEBHOOK).contains("PAYMENT_REFUNDED", "PAYMENT_PARTIALLY_REFUNDED",
                "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE", "PAYMENT_AWAITING_CHARGEBACK_REVERSAL");
    }

    private static EventoAsaas avisoDeEstorno(String evento, String refunds) throws Exception {
        return JsonMapper.builder().build().readValue("""
                {"id":"evt_1","event":"%s","dateCreated":"2026-10-19 10:15:00",
                 "payment":{"id":"pay_1","status":"REFUNDED","billingType":"PIX","value":100.00,"refunds":%s}}
                """.formatted(evento, refunds), EventoAsaas.class);
    }

    @Test
    void estornoSomaOsRefundsConcluidos() throws Exception {
        EventoAsaas evento = avisoDeEstorno("PAYMENT_PARTIALLY_REFUNDED", """
                [{"status":"DONE","value":30.00},{"status":"DONE","value":20.00},
                 {"status":"PENDING","value":5.00},{"status":"CANCELLED","value":7.00}]""");

        assertThat(AsaasAdapter.valorEstornado(evento)).isEqualByComparingTo("50.00");
    }

    @Test
    void estornoTotalSemRefundsValeOValorDaCobranca() throws Exception {
        assertThat(AsaasAdapter.valorEstornado(avisoDeEstorno("PAYMENT_REFUNDED", "null")))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void estornoParcialSemRefundConcluidoNaoDizOValor() throws Exception {
        assertThat(AsaasAdapter.valorEstornado(avisoDeEstorno("PAYMENT_PARTIALLY_REFUNDED", "null"))).isNull();
        assertThat(AsaasAdapter.valorEstornado(avisoDeEstorno("PAYMENT_PARTIALLY_REFUNDED",
                "[{\"status\":\"PENDING\",\"value\":30.00}]"))).isNull();
    }

    @Test
    void avisoQueNaoEEstornoNaoTemValorEstornado() throws Exception {
        assertThat(AsaasAdapter.valorEstornado(avisoDeEstorno("PAYMENT_RECEIVED",
                "[{\"status\":\"DONE\",\"value\":30.00}]"))).isNull();
    }

    @Test
    void formaDePagamentoForaDePixEBoletoEErro() {
        CobrancaAsaas cartao = new CobrancaAsaas("pay_1", "EDK-B-1", "PENDING", "CREDIT_CARD", false, BigDecimal.TEN,
                BigDecimal.ONE, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> AsaasAdapter.paraCobranca(cartao)).isInstanceOf(IllegalStateException.class);
    }

    static Stream<Arguments> eventos() {
        return Stream.of(
                Arguments.of("webhook-payment-created.json", TipoEventoProvedor.COBRANCA_CRIADA,
                        StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO),
                Arguments.of("webhook-payment-received.json", TipoEventoProvedor.PAGAMENTO_RECEBIDO,
                        StatusCobrancaProvedor.RECEBIDA),
                Arguments.of("webhook-payment-received-boleto.json", TipoEventoProvedor.PAGAMENTO_RECEBIDO,
                        StatusCobrancaProvedor.RECEBIDA),
                Arguments.of("webhook-payment-deleted.json", TipoEventoProvedor.COBRANCA_REMOVIDA,
                        StatusCobrancaProvedor.REMOVIDA),
                Arguments.of("webhook-payment-overdue.json", TipoEventoProvedor.NAO_TRATADO,
                        StatusCobrancaProvedor.VENCIDA));
    }

    @ParameterizedTest
    @MethodSource("eventos")
    void mapeiaEventosDeCobranca(String arquivo, TipoEventoProvedor tipo, StatusCobrancaProvedor status) {
        EventoProvedor evento = adapter.interpretarEvento(fixture(arquivo));

        assertThat(evento.tipo()).isEqualTo(tipo);
        assertThat(evento.idEvento()).startsWith("evt_");
        assertThat(evento.cobranca().status()).isEqualTo(status);
        assertThat(evento.situacaoConta()).isNull();
        assertThat(evento.dataEvento()).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void eventoDeBoletoRecebidoUsaONossoNumeroComoTransacao() {
        EventoProvedor evento = adapter.interpretarEvento(fixture("webhook-payment-received-boleto.json"));

        assertThat(evento.cobranca().forma()).isEqualTo(FormaPagamento.BOLETO);
        assertThat(evento.cobranca().idTransacao()).isEqualTo("13638753");
        assertThat(evento.cobranca().valorPago()).isEqualByComparingTo("88.80");
    }

    @Test
    void eventoDeContaLeASituacaoDoPayloadENaoDoNomeDoEvento() {
        // O evento diz AWAITING_APPROVAL, mas a foto da conta ja esta aprovada (achado A11).
        EventoProvedor evento = adapter.interpretarEvento(fixture("webhook-account-awaiting-approval.json"));

        assertThat(evento.tipo()).isEqualTo(TipoEventoProvedor.SITUACAO_CONTA_ALTERADA);
        assertThat(evento.tipoOriginal()).isEqualTo("ACCOUNT_STATUS_GENERAL_APPROVAL_AWAITING_APPROVAL");
        assertThat(evento.idContaExterna()).isEqualTo("3d134583-fe86-4aad-800f-6c6e63629478");
        assertThat(evento.cobranca()).isNull();
        assertThat(evento.situacaoConta().geral()).isEqualTo(SituacaoCadastral.APROVADO);
    }

    @Test
    void eventoDeContaSemSituacaoNoPayloadDeixaSituacaoNula() {
        EventoProvedor evento = adapter.interpretarEvento("""
                {"id":"evt_1","event":"ACCOUNT_STATUS_DOCUMENT_APPROVED","dateCreated":"2026-10-19 11:40:19",
                 "account":{"id":"acc_1","ownerId":"pai"}}
                """);

        assertThat(evento.tipo()).isEqualTo(TipoEventoProvedor.SITUACAO_CONTA_ALTERADA);
        assertThat(evento.situacaoConta()).isNull();
    }

    @Test
    void eventoIlegivelEErroDeRequisicao() {
        assertThatThrownBy(() -> adapter.interpretarEvento("nao e json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.interpretarEvento("[]"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void webhookSoEAutenticoComOTokenCadastrado() {
        assertThat(adapter.webhookAutentico(TOKEN_WEBHOOK)).isTrue();
        assertThat(adapter.webhookAutentico("outro-token")).isFalse();
        assertThat(adapter.webhookAutentico("")).isFalse();
        assertThat(adapter.webhookAutentico(null)).isFalse();
    }

    @Test
    void semTokenConfiguradoNenhumWebhookEAutentico() {
        AsaasAdapter semToken = adapterCom(new AsaasProperties(URL, CHAVE_PAI,
                "https://tunel.example.com/webhooks/asaas", "", "falhas@example.com"));

        assertThat(semToken.webhookAutentico("")).isFalse();
        assertThat(semToken.webhookAutentico(null)).isFalse();
    }

    // ---- Webhook da conta-pai ----

    @Test
    void garantirWebhookDaContaPaiCadastraSoOsEventosDePagamento() {
        servidor.expect(requestTo(URL + "/v3/webhooks?limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CHAVE_PAI))
                .andRespond(withSuccess(listaCom("{\"id\":\"wh-1\",\"url\":\"https://outro.example.com/hook\"}"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/v3/webhooks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", CHAVE_PAI))
                .andExpect(jsonPath("$.name").value("Eduka"))
                .andExpect(jsonPath("$.url").value("https://tunel.example.com/webhooks/asaas"))
                .andExpect(jsonPath("$.email").value("falhas@example.com"))
                .andExpect(jsonPath("$.authToken").value(TOKEN_WEBHOOK))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.interrupted").value(false))
                .andExpect(jsonPath("$.sendType").value("SEQUENTIALLY"))
                .andExpect(jsonPath("$.events.length()").value(AsaasAdapter.EVENTOS_WEBHOOK_CONTA_PAI.size()))
                .andExpect(jsonPath("$.events[0]").value("PAYMENT_CREATED"))
                .andRespond(withSuccess("{\"id\":\"wh-2\"}", MediaType.APPLICATION_JSON));

        adapter.garantirWebhookDaContaPai();

        servidor.verify();
        assertThat(AsaasAdapter.EVENTOS_WEBHOOK_CONTA_PAI)
                .isNotEmpty()
                .allMatch(evento -> evento.startsWith("PAYMENT_"));
    }

    @Test
    void garantirWebhookDaContaPaiNaoDuplicaOJaCadastrado() {
        servidor.expect(requestTo(URL + "/v3/webhooks?limit=100"))
                .andRespond(withSuccess(listaCom("{\"id\":\"wh-1\",\"url\":\"https://tunel.example.com/webhooks/asaas\"}"),
                        MediaType.APPLICATION_JSON));

        adapter.garantirWebhookDaContaPai();

        // Um POST inesperado falharia aqui.
        servidor.verify();
    }

    @Test
    void garantirWebhookDaContaPaiSemWebhookOuChaveNaoChamaOAsaas(CapturedOutput saida) {
        AsaasAdapter semEmail = adapterCom(new AsaasProperties(URL, CHAVE_PAI,
                "https://tunel.example.com/webhooks/asaas", TOKEN_WEBHOOK, ""));
        servidor.expect(never(), anything());
        semEmail.garantirWebhookDaContaPai();
        servidor.verify();

        AsaasAdapter semChave = adapterCom(new AsaasProperties(URL, "",
                "https://tunel.example.com/webhooks/asaas", TOKEN_WEBHOOK, "falhas@example.com"));
        servidor.expect(never(), anything());
        semChave.garantirWebhookDaContaPai();
        servidor.verify();

        assertThat(saida.getOut()).contains("Webhook da conta-pai nao cadastrado");
    }

    // ---- A03: erros HTTP ----

    @Test
    void erro400ViraRecusaComADescricaoDoAsaas() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("erro-400.json")));

        assertThatThrownBy(() -> adapter.alterarFormaEVencimento(CREDENCIAL, "pay_1", FormaPagamento.PIX,
                LocalDate.of(2026, 10, 30)))
                .isInstanceOf(ProvedorRecusouException.class)
                .hasMessage("Não é possível alterar a forma de pagamento de cobranças recebidas ou confirmadas.");
    }

    @Test
    void mensagemDaRecusaNaoTrazCnpj() {
        servidor.expect(requestTo(URL + "/v3/accounts"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("erro-400-cnpj-em-uso.json")));

        assertThatThrownBy(() -> adapter.criarConta(NOVA_CONTA))
                .isInstanceOf(ProvedorRecusouException.class)
                .hasMessageNotContaining("11222333000181")
                .hasMessageContaining("já está em uso");
    }

    @Test
    void mensagemDaRecusaNaoTrazEmailECabeEm500Caracteres() {
        String longa = "x".repeat(600);
        assertThat(AsaasAdapter.sanear("O e-mail ana@example.com e o CPF 123.456.789-09 sao invalidos"))
                .doesNotContain("ana@example.com")
                .doesNotContain("123.456.789-09");
        assertThat(AsaasAdapter.sanear(longa)).hasSize(500);
    }

    @Test
    void erro400SemCorpoViraRecusaGenerica() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.consultarCobranca(CREDENCIAL, "pay_1"))
                .isInstanceOf(ProvedorRecusouException.class)
                .hasMessage("O provedor de pagamento recusou o pedido");
    }

    @ParameterizedTest
    @CsvSource({ "401", "403" })
    void chaveInvalidaViraProvedorNaoConfigurado(int status) {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("erro-401.json")));

        assertThatThrownBy(() -> adapter.consultarCobranca(CREDENCIAL, "pay_1"))
                .isInstanceOf(ProvedorNaoConfiguradoException.class);
    }

    @ParameterizedTest
    @CsvSource({ "429", "500", "502", "503" })
    void erroTemporarioViraIndisponivel(int status) {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("erro-500.json")));

        assertThatThrownBy(() -> adapter.consultarCobranca(CREDENCIAL, "pay_1"))
                .isInstanceOf(ProvedorIndisponivelException.class);
    }

    @Test
    void timeoutViraIndisponivel() {
        servidor.expect(requestTo(URL + "/v3/payments"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> adapter.criarCobranca(CREDENCIAL, new NovaCobrancaProvedor("cus_1",
                FormaPagamento.BOLETO, BigDecimal.TEN, LocalDate.of(2026, 10, 24), "Mensalidade", "EDK-B-1")))
                .isInstanceOf(ProvedorIndisponivelException.class);
    }

    @Test
    void removerCobrancaInexistenteESucesso() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> adapter.removerCobranca(CREDENCIAL, "pay_1")).doesNotThrowAnyException();
    }

    @Test
    void removerCobrancaPagaERecusa() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("erro-400-remover-paga.json")));

        assertThatThrownBy(() -> adapter.removerCobranca(CREDENCIAL, "pay_1"))
                .isInstanceOf(ProvedorRecusouException.class);
    }

    @Test
    void consultarCobrancaInexistenteERecusa() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.consultarCobranca(CREDENCIAL, "pay_1"))
                .isInstanceOf(ProvedorRecusouException.class);
    }

    @Test
    void pixSemChaveNaContaEInconclusivo() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_1/pixQrCode"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("erro-400-pix-sem-chave.json")));

        assertThatThrownBy(() -> adapter.obterPix(CREDENCIAL, "pay_1"))
                .isInstanceOf(ProvedorIndisponivelException.class);
    }

    @Test
    void semChaveDaContaPaiNaoChamaOAsaas() {
        AsaasAdapter semChave = adapterCom(new AsaasProperties(URL, "", null, null, null));
        servidor.expect(never(), anything());

        assertThatThrownBy(() -> semChave.criarConta(NOVA_CONTA))
                .isInstanceOf(ProvedorNaoConfiguradoException.class);
        assertThatThrownBy(() -> semChave.buscarContaPorCnpj("11222333000181"))
                .isInstanceOf(ProvedorNaoConfiguradoException.class);
        assertThatThrownBy(semChave::credencialDaContaPai).isInstanceOf(ProvedorNaoConfiguradoException.class);
        servidor.verify();
    }

    @Test
    void credencialDaContaPaiVemDaConfiguracao() {
        assertThat(adapter.credencialDaContaPai().valor()).isEqualTo(CHAVE_PAI);
    }

    @Test
    void semCredencialDaEscolaNaoChamaOAsaas() {
        servidor.expect(never(), anything());

        assertThatThrownBy(() -> adapter.consultarCobranca(new CredencialProvedor(""), "pay_1"))
                .isInstanceOf(ProvedorNaoConfiguradoException.class);
        assertThatThrownBy(() -> adapter.consultarSituacaoConta(null))
                .isInstanceOf(ProvedorNaoConfiguradoException.class);
        servidor.verify();
    }

    // ---- A04: evento ignora campos desconhecidos e sai sem dados pessoais ----

    @Test
    void interpretarEventoIgnoraCamposNovosERemoveDadosPessoais() {
        String corpo = """
                {"id":"evt_1&2","event":"PAYMENT_RECEIVED","dateCreated":"2026-10-19 11:14:19","campoNovo":{"x":1},
                 "account":{"id":"acc_1","ownerId":null,"email":"escola@example.com"},
                 "payment":{"id":"pay_1","status":"RECEIVED","billingType":"PIX","deleted":false,"value":10.0,
                   "externalReference":"EDK-B-1","outroCampoNovo":true,"pixTransaction":"tx-1",
                   "customer":{"id":"cus_1","cpfCnpj":"12345678909","email":"aluno@example.com",
                     "phone":"3133334444","mobilePhone":"31999998888"},
                   "bankAccount":{"agency":"1234","account":"98765"},
                   "creditCard":{"creditCardNumber":"4111"},"creditCardToken":"tok_cartao"}}
                """;

        EventoProvedor evento = adapter.interpretarEvento(corpo);

        assertThat(evento.cobranca().idCobrancaExterna()).isEqualTo("pay_1");
        assertThat(evento.payloadSaneado())
                .contains("\"pay_1\"")
                .contains("campoNovo")
                .doesNotContain("12345678909")
                .doesNotContain("aluno@example.com")
                .doesNotContain("escola@example.com")
                .doesNotContain("3133334444")
                .doesNotContain("31999998888")
                .doesNotContain("98765")
                .doesNotContain("creditCard")
                .doesNotContain("tok_cartao");
    }

    // ---- A05: nenhum link do Asaas no tipo neutro ----

    @Test
    void cobrancaNeutraNaoTemLinkDeFaturaNemDeBoleto() {
        assertThat(Arrays.stream(CobrancaProvedor.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("invoiceUrl", "bankSlipUrl", "urlFatura", "urlBoleto");

        servidor.expect(requestTo(URL + "/v3/payments/pay_1170f83b80iun8gy"))
                .andRespond(withSuccess(fixture("cobranca-pix-criada.json"), MediaType.APPLICATION_JSON));
        CobrancaProvedor cobranca = adapter.consultarCobranca(CREDENCIAL, "pay_1170f83b80iun8gy");

        assertThat(cobranca.toString()).doesNotContain("sandbox.asaas.com/i/");
    }

    // ---- A06: atalhos de Sandbox so no Sandbox ----

    @Test
    void atalhosDoSandboxFalhamForaDoSandbox() {
        AsaasAdapter producao = adapterCom(new AsaasProperties("https://api.asaas.com", CHAVE_PAI, null, null, null));
        servidor.expect(never(), anything());

        assertThatThrownBy(() -> producao.sandboxConfirmarPagamento(CREDENCIAL, "pay_1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> producao.sandboxAprovarConta(CREDENCIAL))
                .isInstanceOf(IllegalStateException.class);
        servidor.verify();
    }

    // ---- A07: log sem credencial ----

    @Test
    void logDaChamadaNaoTemCredencial(CapturedOutput saida) {
        servidor.expect(requestTo(URL + "/v3/accounts"))
                .andRespond(withSuccess(fixture("conta-criada.json"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/v3/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("erro-401.json")));

        adapter.criarConta(NOVA_CONTA);
        assertThatThrownBy(() -> adapter.consultarCobranca(CREDENCIAL, "pay_1"));

        assertThat(saida.getAll())
                .contains("criarConta")
                .contains("consultarCobranca")
                .doesNotContain(CHAVE_PAI)
                .doesNotContain(CREDENCIAL.valor())
                .doesNotContain("CHAVE_FICTICIA_DA_SUBCONTA")
                .doesNotContain(TOKEN_WEBHOOK)
                .doesNotContain("11222333000181");
    }

    @Test
    void propertiesNaoMostramChaveNemToken() {
        assertThat(PROPERTIES.toString()).doesNotContain(CHAVE_PAI).doesNotContain(TOKEN_WEBHOOK);
        assertThat(PROPERTIES.configurado()).isTrue();
        assertThat(PROPERTIES.sandbox()).isTrue();
        assertThat(new AsaasProperties(URL, " ", null, null, null).configurado()).isFalse();
    }

    // ---- PDF do boleto ----

    private static final byte[] PDF = "%PDF-1.4 boleto".getBytes(StandardCharsets.US_ASCII);

    private void linkDoBoleto(String bankSlipUrl) {
        servidor.expect(requestTo(URL + "/v3/payments/pay_boleto"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", CREDENCIAL.valor()))
                .andRespond(withSuccess("{\"id\":\"pay_boleto\",\"bankSlipUrl\":\"" + bankSlipUrl + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void baixarBoletoPdfSegueOLinkSemMandarAChave() {
        linkDoBoleto("https://sandbox.asaas.com/b/pdf/abc123");
        servidor.expect(requestTo("https://sandbox.asaas.com/b/pdf/abc123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("access_token"))
                .andRespond(withSuccess(PDF, MediaType.APPLICATION_PDF));

        assertThat(adapter.baixarBoletoPdf(CREDENCIAL, "pay_boleto")).isEqualTo(PDF);
        servidor.verify();
    }

    @Test
    void baixarBoletoPdfRecusaLinkForaDoDominioDoAsaas() {
        linkDoBoleto("https://asaas.com.exemplo-malicioso.com/boleto.pdf");

        assertThatThrownBy(() -> adapter.baixarBoletoPdf(CREDENCIAL, "pay_boleto"))
                .isInstanceOf(ProvedorIndisponivelException.class);
        servidor.verify();
    }

    @Test
    void baixarBoletoPdfRecusaLinkSemHttps() {
        linkDoBoleto("http://sandbox.asaas.com/b/pdf/abc123");

        assertThatThrownBy(() -> adapter.baixarBoletoPdf(CREDENCIAL, "pay_boleto"))
                .isInstanceOf(ProvedorIndisponivelException.class);
    }

    @Test
    void cobrancaSemBoletoNoProvedorERecusada() {
        servidor.expect(requestTo(URL + "/v3/payments/pay_boleto"))
                .andRespond(withSuccess("{\"id\":\"pay_boleto\",\"bankSlipUrl\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.baixarBoletoPdf(CREDENCIAL, "pay_boleto"))
                .isInstanceOf(ProvedorRecusouException.class);
    }

    @Test
    void respostaQueNaoEPdfEInconclusiva() {
        linkDoBoleto("https://sandbox.asaas.com/b/pdf/abc123");
        servidor.expect(requestTo("https://sandbox.asaas.com/b/pdf/abc123"))
                .andRespond(withSuccess("<html>erro</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> adapter.baixarBoletoPdf(CREDENCIAL, "pay_boleto"))
                .isInstanceOf(ProvedorIndisponivelException.class);
    }

    @Test
    void baixarBoletoPdfSegueRedirecionamentoDentroDoAsaas() {
        linkDoBoleto("https://sandbox.asaas.com/b/pdf/abc123");
        servidor.expect(requestTo("https://sandbox.asaas.com/b/pdf/abc123"))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create("/arquivos/boleto-abc123.pdf")));
        servidor.expect(requestTo("https://sandbox.asaas.com/arquivos/boleto-abc123.pdf"))
                .andExpect(headerDoesNotExist("access_token"))
                .andRespond(withSuccess(PDF, MediaType.APPLICATION_PDF));

        assertThat(adapter.baixarBoletoPdf(CREDENCIAL, "pay_boleto")).isEqualTo(PDF);
        servidor.verify();
    }

    @Test
    void baixarBoletoPdfNaoSegueRedirecionamentoParaForaDoAsaas() {
        linkDoBoleto("https://sandbox.asaas.com/b/pdf/abc123");
        servidor.expect(requestTo("https://sandbox.asaas.com/b/pdf/abc123"))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create("https://exemplo-malicioso.com/x.pdf")));

        assertThatThrownBy(() -> adapter.baixarBoletoPdf(CREDENCIAL, "pay_boleto"))
                .isInstanceOf(ProvedorIndisponivelException.class);
        servidor.verify();
    }
}
