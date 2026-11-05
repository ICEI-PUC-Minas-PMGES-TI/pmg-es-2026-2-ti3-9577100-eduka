package br.pucminas.eduka.backend.pagamento.asaas;

import java.math.BigDecimal;
import java.util.Locale;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import br.pucminas.eduka.backend.pagamento.PaymentProvider;
import br.pucminas.eduka.backend.pagamento.ProvedorIndisponivelException;
import br.pucminas.eduka.backend.pagamento.ProvedorNaoConfiguradoException;
import br.pucminas.eduka.backend.pagamento.ProvedorRecusouException;
import br.pucminas.eduka.backend.pagamento.asaas.dto.AlteracaoCobrancaAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.ChavePixAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.ClienteAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.CobrancaAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.ContaAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.ErroAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.EventoAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.LinhaDigitavelAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.LinkBoletoAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.ListaAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.NovaChavePixAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.NovaCobrancaAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.NovaContaAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.NovoClienteAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.PixQrCodeAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.SituacaoContaAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.WebhookAsaas;
import br.pucminas.eduka.backend.pagamento.asaas.dto.WebhookCadastradoAsaas;
import br.pucminas.eduka.backend.pagamento.tipos.BoletoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.ContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosNovaConta;
import br.pucminas.eduka.backend.pagamento.tipos.DadosPagador;
import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.NovaCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.PixProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoCadastral;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Unica classe que conhece o Asaas: URLs, header {@code access_token}, JSON,
 * nomes de status e de eventos. Tudo sai daqui ja nos tipos neutros de
 * {@code pagamento.tipos}.
 *
 * <p>
 * Erros HTTP: 400 vira {@link ProvedorRecusouException} com a mensagem do
 * Asaas saneada; 401/403 vira {@link ProvedorNaoConfiguradoException}; 429,
 * 5xx, timeout e erro de rede viram {@link ProvedorIndisponivelException}
 * (inconclusivo). O log de cada chamada tem operacao, id, status HTTP e
 * duracao, nunca o corpo nem a chave.
 * </p>
 */
public class AsaasAdapter implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(AsaasAdapter.class);

    private static final String HEADER_CHAVE = "access_token";
    private static final ZoneId FUSO_ASAAS = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA_ASAAS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_MENSAGEM = 500;
    /** Quantos redirecionamentos o download do PDF do boleto segue. */
    private static final int MAX_REDIRECIONAMENTOS_PDF = 3;

    /** Chaves removidas do payload do evento, em qualquer nivel. */
    private static final Set<String> CHAVES_SENSIVEIS = Set.of("cpfCnpj", "email", "phone", "mobilePhone",
            "bankAccount");
    private static final String PREFIXO_CARTAO = "creditCard";

    /** CPF/CNPJ (com ou sem pontuacao) e e-mail citados na mensagem de erro do Asaas. */
    private static final Pattern DOCUMENTO = Pattern.compile("\\d[\\d./-]{9,}\\d");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    /** Chave PIX que ja serve ou vai servir para receber. */
    private static final Set<String> CHAVE_PIX_UTIL = Set.of("ACTIVE", "AWAITING_ACTIVATION");

    /**
     * Eventos assinados no webhook da conta filha (lista aceita pelo Sandbox no
     * spike, mais os de estorno e contestacao auditados pela RF22.2).
     */
    static final List<String> EVENTOS_WEBHOOK = List.of(
            "PAYMENT_CREATED", "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED", "PAYMENT_DELETED",
            "PAYMENT_REFUNDED", "PAYMENT_PARTIALLY_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED",
            "PAYMENT_CHARGEBACK_DISPUTE", "PAYMENT_AWAITING_CHARGEBACK_REVERSAL",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_APPROVED", "ACCOUNT_STATUS_GENERAL_APPROVAL_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_PENDING", "ACCOUNT_STATUS_GENERAL_APPROVAL_REJECTED",
            "ACCOUNT_STATUS_DOCUMENT_APPROVED", "ACCOUNT_STATUS_DOCUMENT_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_DOCUMENT_PENDING", "ACCOUNT_STATUS_DOCUMENT_REJECTED",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_APPROVED", "ACCOUNT_STATUS_COMMERCIAL_INFO_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_PENDING", "ACCOUNT_STATUS_COMMERCIAL_INFO_REJECTED",
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_APPROVED", "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_PENDING", "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_REJECTED");

    /** Eventos do webhook da conta-pai: so os de pagamento; os ACCOUNT_STATUS_ sao das contas filhas. */
    static final List<String> EVENTOS_WEBHOOK_CONTA_PAI = EVENTOS_WEBHOOK.stream()
            .filter(evento -> evento.startsWith("PAYMENT_"))
            .toList();

    /** Maior pagina aceita pelo Asaas nas listagens. */
    private static final int LIMITE_LISTAGEM = 100;

    private static final ParameterizedTypeReference<ListaAsaas<ContaAsaas>> LISTA_CONTAS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<ListaAsaas<ClienteAsaas>> LISTA_CLIENTES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<ListaAsaas<CobrancaAsaas>> LISTA_COBRANCAS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<ListaAsaas<ChavePixAsaas>> LISTA_CHAVES_PIX = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<ListaAsaas<WebhookCadastradoAsaas>> LISTA_WEBHOOKS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final AsaasProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();

    public AsaasAdapter(RestClient.Builder restClientBuilder, AsaasProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "Eduka")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ---- Contas ----

    @Override
    public ContaProvedor criarConta(DadosNovaConta dados) {
        String chavePai = chaveDaContaPai();
        NovaContaAsaas corpo = new NovaContaAsaas(dados.razaoSocial(), dados.email(), dados.cnpj(),
                dados.telefoneCelular(), dados.faturamentoMensal(), dados.tipoEmpresa().name(), dados.logradouro(),
                dados.numero(), dados.complemento(), dados.bairro(), dados.cep(), webhooks());
        ContaAsaas conta = corpo("criarConta", null, () -> restClient.post()
                .uri("/v3/accounts")
                .header(HEADER_CHAVE, chavePai)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toEntity(ContaAsaas.class));
        String chave = conta.chave();
        return new ContaProvedor(conta.id(), conta.walletId(), chave == null ? null : new CredencialProvedor(chave));
    }

    @Override
    public Optional<ContaProvedor> buscarContaPorCnpj(String cnpj) {
        String chavePai = chaveDaContaPai();
        ListaAsaas<ContaAsaas> contas = corpo("buscarContaPorCnpj", null, () -> restClient.get()
                .uri(uri -> uri.path("/v3/accounts").queryParam("cpfCnpj", cnpj).build())
                .header(HEADER_CHAVE, chavePai)
                .retrieve()
                .toEntity(LISTA_CONTAS));
        // A busca nunca traz a chave da conta (P-13).
        return contas.itens().stream().findFirst()
                .map(conta -> new ContaProvedor(conta.id(), conta.walletId(), null));
    }

    @Override
    public SituacaoContaProvedor consultarSituacaoConta(CredencialProvedor credencialDaConta) {
        String chave = exigir(credencialDaConta);
        SituacaoContaAsaas situacao = corpo("consultarSituacaoConta", null, () -> restClient.get()
                .uri("/v3/myAccount/status")
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toEntity(SituacaoContaAsaas.class));
        return paraSituacao(situacao);
    }

    /**
     * Lista as chaves antes de criar: uma segunda criacao seguida e recusada
     * com 400 "excesso de consultas" (achado A17). Por isso, aqui, 400 e
     * tratado como inconclusivo e a ativacao pode ser repetida.
     */
    @Override
    public void garantirChavePix(CredencialProvedor credencialDaConta) {
        String chave = exigir(credencialDaConta);
        ListaAsaas<ChavePixAsaas> chaves = corpo("listarChavesPix", null, true, () -> restClient.get()
                .uri("/v3/pix/addressKeys")
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toEntity(LISTA_CHAVES_PIX));
        boolean temChave = chaves.itens().stream().anyMatch(c -> CHAVE_PIX_UTIL.contains(c.status()));
        if (temChave) {
            return;
        }
        chamar("criarChavePix", null, true, () -> restClient.post()
                .uri("/v3/pix/addressKeys")
                .header(HEADER_CHAVE, chave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NovaChavePixAsaas("EVP"))
                .retrieve()
                .toBodilessEntity());
    }

    /** A chave da conta-pai vem de {@code ASAAS_API_KEY}, nunca do banco. */
    @Override
    public CredencialProvedor credencialDaContaPai() {
        return new CredencialProvedor(chaveDaContaPai());
    }

    // ---- Clientes e cobrancas ----

    @Override
    public String obterOuCriarCliente(CredencialProvedor credencial, DadosPagador pagador) {
        String chave = exigir(credencial);
        String referencia = pagador.referenciaExterna();
        ListaAsaas<ClienteAsaas> existentes = corpo("buscarCliente", referencia, () -> restClient.get()
                .uri(uri -> uri.path("/v3/customers").queryParam("externalReference", referencia).build())
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toEntity(LISTA_CLIENTES));
        Optional<String> existente = existentes.itens().stream().map(ClienteAsaas::id).findFirst();
        if (existente.isPresent()) {
            return existente.get();
        }
        NovoClienteAsaas corpo = new NovoClienteAsaas(pagador.nome(), pagador.cpfCnpj(), pagador.email(), referencia,
                true);
        ClienteAsaas criado = corpo("criarCliente", referencia, () -> restClient.post()
                .uri("/v3/customers")
                .header(HEADER_CHAVE, chave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toEntity(ClienteAsaas.class));
        return criado.id();
    }

    @Override
    public CobrancaProvedor criarCobranca(CredencialProvedor credencial, NovaCobrancaProvedor cobranca) {
        String chave = exigir(credencial);
        NovaCobrancaAsaas corpo = new NovaCobrancaAsaas(cobranca.idCliente(), cobranca.forma().name(),
                cobranca.valor(), cobranca.vencimento(), cobranca.descricao(), cobranca.referenciaExterna());
        CobrancaAsaas criada = corpo("criarCobranca", cobranca.referenciaExterna(), () -> restClient.post()
                .uri("/v3/payments")
                .header(HEADER_CHAVE, chave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toEntity(CobrancaAsaas.class));
        return paraCobranca(criada);
    }

    @Override
    public Optional<CobrancaProvedor> buscarCobrancaPorReferencia(CredencialProvedor credencial,
            String referenciaExterna) {
        String chave = exigir(credencial);
        ListaAsaas<CobrancaAsaas> cobrancas = corpo("buscarCobrancaPorReferencia", referenciaExterna,
                () -> restClient.get()
                        .uri(uri -> uri.path("/v3/payments").queryParam("externalReference", referenciaExterna).build())
                        .header(HEADER_CHAVE, chave)
                        .retrieve()
                        .toEntity(LISTA_COBRANCAS));
        // O Asaas ja omite as removidas (achado A5); o filtro fica por seguranca.
        return cobrancas.itens().stream()
                .filter(c -> !c.removida())
                .findFirst()
                .map(AsaasAdapter::paraCobranca);
    }

    @Override
    public CobrancaProvedor consultarCobranca(CredencialProvedor credencial, String idCobrancaExterna) {
        String chave = exigir(credencial);
        CobrancaAsaas cobranca = corpo("consultarCobranca", idCobrancaExterna, () -> restClient.get()
                .uri("/v3/payments/{id}", idCobrancaExterna)
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toEntity(CobrancaAsaas.class));
        return paraCobranca(cobranca);
    }

    @Override
    public CobrancaProvedor alterarFormaEVencimento(CredencialProvedor credencial, String idCobrancaExterna,
            FormaPagamento forma, LocalDate vencimento) {
        String chave = exigir(credencial);
        AlteracaoCobrancaAsaas corpo = new AlteracaoCobrancaAsaas(forma.name(), vencimento);
        CobrancaAsaas alterada = corpo("alterarFormaEVencimento", idCobrancaExterna, () -> restClient.put()
                .uri("/v3/payments/{id}", idCobrancaExterna)
                .header(HEADER_CHAVE, chave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toEntity(CobrancaAsaas.class));
        return paraCobranca(alterada);
    }

    /** 404 (inexistente) e 200 repetido (ja removida, achado A4) contam como sucesso. */
    @Override
    public void removerCobranca(CredencialProvedor credencial, String idCobrancaExterna) {
        String chave = exigir(credencial);
        try {
            chamar("removerCobranca", idCobrancaExterna, false, () -> restClient.delete()
                    .uri("/v3/payments/{id}", idCobrancaExterna)
                    .header(HEADER_CHAVE, chave)
                    .retrieve()
                    .toBodilessEntity());
        } catch (NaoEncontradaNoProvedorException jaRemovida) {
            // nada a fazer
        }
    }

    /**
     * Conta sem chave PIX ativa responde 400 aqui (achado A1). A chave aparece
     * em segundos, entao 400 e tratado como inconclusivo.
     */
    @Override
    public PixProvedor obterPix(CredencialProvedor credencial, String idCobrancaExterna) {
        String chave = exigir(credencial);
        PixQrCodeAsaas pix = corpo("obterPix", idCobrancaExterna, true, () -> restClient.get()
                .uri("/v3/payments/{id}/pixQrCode", idCobrancaExterna)
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toEntity(PixQrCodeAsaas.class));
        return new PixProvedor(pix.encodedImage(), pix.payload(), dataHora(pix.expirationDate()));
    }

    @Override
    public BoletoProvedor obterLinhaDigitavel(CredencialProvedor credencial, String idCobrancaExterna) {
        String chave = exigir(credencial);
        LinhaDigitavelAsaas linha = corpo("obterLinhaDigitavel", idCobrancaExterna, () -> restClient.get()
                .uri("/v3/payments/{id}/identificationField", idCobrancaExterna)
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toEntity(LinhaDigitavelAsaas.class));
        return new BoletoProvedor(linha.identificationField(), linha.barCode(), linha.nossoNumero());
    }

    /**
     * Le o {@code bankSlipUrl} da cobranca e baixa o PDF por ele. O link e
     * publico: a chave da conta nao vai junto. So segue link https de dominio
     * do Asaas, para uma resposta adulterada nao fazer o backend buscar outro
     * endereco.
     */
    @Override
    public byte[] baixarBoletoPdf(CredencialProvedor credencial, String idCobrancaExterna) {
        String chave = exigir(credencial);
        LinkBoletoAsaas link = corpo("linkBoleto", idCobrancaExterna, () -> restClient.get()
                .uri("/v3/payments/{id}", idCobrancaExterna)
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toEntity(LinkBoletoAsaas.class));
        URI atual = linkConfiavel(link.bankSlipUrl());
        // O cliente HTTP nao segue redirecionamento sozinho: segue aqui, so para dominio do Asaas.
        for (int saltos = 0; saltos <= MAX_REDIRECIONAMENTOS_PDF; saltos++) {
            URI alvo = atual;
            ResponseEntity<byte[]> resposta = chamar("baixarBoletoPdf", idCobrancaExterna, false, () -> restClient.get()
                    .uri(alvo)
                    .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .toEntity(byte[].class));
            if (resposta.getStatusCode().is3xxRedirection()) {
                URI destino = resposta.getHeaders().getLocation();
                if (destino == null) {
                    throw new ProvedorIndisponivelException("Redirecionamento do boleto sem destino");
                }
                atual = linkConfiavel(alvo.resolve(destino).toString());
                continue;
            }
            byte[] conteudo = resposta.getBody();
            if (conteudo == null || !ehPdf(conteudo)) {
                throw new ProvedorIndisponivelException("Provedor de pagamento nao devolveu o PDF do boleto");
            }
            return conteudo;
        }
        throw new ProvedorIndisponivelException("Redirecionamentos demais ao baixar o boleto");
    }

    private static URI linkConfiavel(String bankSlipUrl) {
        if (bankSlipUrl == null || bankSlipUrl.isBlank()) {
            throw new ProvedorRecusouException("Cobranca sem boleto no provedor");
        }
        URI uri;
        try {
            uri = URI.create(bankSlipUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new ProvedorIndisponivelException("Link do boleto invalido");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !(host.equals("asaas.com") || host.endsWith(".asaas.com"))) {
            throw new ProvedorIndisponivelException("Link do boleto fora do dominio do provedor");
        }
        return uri;
    }

    /** Todo PDF comeca com {@code %PDF}. */
    private static boolean ehPdf(byte[] conteudo) {
        return conteudo.length > 4 && conteudo[0] == '%' && conteudo[1] == 'P' && conteudo[2] == 'D'
                && conteudo[3] == 'F';
    }

    // ---- Webhook ----

    /** Token do header {@code Asaas-Access-Token}, comparado em tempo constante. */
    @Override
    public boolean webhookAutentico(String tokenRecebido) {
        String esperado = properties.webhookToken();
        if (esperado == null || esperado.isBlank()) {
            log.warn("Aviso do Asaas recusado: ASAAS_WEBHOOK_TOKEN nao configurado");
            return false;
        }
        if (tokenRecebido == null) {
            return false;
        }
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                tokenRecebido.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public EventoProvedor interpretarEvento(String corpoJson) {
        try {
            JsonNode raiz = json.readTree(corpoJson);
            if (raiz == null || !raiz.isObject()) {
                throw new IllegalArgumentException("Evento do provedor ilegivel");
            }
            EventoAsaas evento = json.treeToValue(raiz, EventoAsaas.class);
            TipoEventoProvedor tipo = tipoDoEvento(evento.event());
            CobrancaProvedor cobranca = evento.payment() == null ? null : paraCobranca(evento.payment());
            SituacaoContaProvedor situacao = tipo == TipoEventoProvedor.SITUACAO_CONTA_ALTERADA
                    && evento.accountStatus() != null ? paraSituacao(evento.accountStatus()) : null;
            String idConta = evento.account() == null ? null : evento.account().id();
            return new EventoProvedor(evento.id(), tipo, evento.event(), idConta, cobranca, situacao,
                    dataDoEvento(evento.dateCreated()), json.writeValueAsString(sanear(raiz)), valorEstornado(evento));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Evento do provedor ilegivel");
        }
    }

    /**
     * Mesmo corpo do webhook das contas filhas, mas so com os eventos de
     * pagamento. Confere pela URL antes de criar, para cada reinicio nao
     * cadastrar outro.
     */
    @Override
    public void garantirWebhookDaContaPai() {
        if (!properties.configurado()) {
            log.warn("Webhook da conta-pai nao cadastrado: ASAAS_API_KEY nao configurada");
            return;
        }
        if (!properties.webhookConfigurado()) {
            log.warn("Webhook da conta-pai nao cadastrado: webhook do Asaas nao configurado");
            return;
        }
        String chavePai = chaveDaContaPai();
        ListaAsaas<WebhookCadastradoAsaas> cadastrados = corpo("listarWebhooks", null, () -> restClient.get()
                .uri(uri -> uri.path("/v3/webhooks").queryParam("limit", LIMITE_LISTAGEM).build())
                .header(HEADER_CHAVE, chavePai)
                .retrieve()
                .toEntity(LISTA_WEBHOOKS));
        boolean jaCadastrado = cadastrados.itens().stream()
                .anyMatch(w -> properties.webhookUrl().equals(w.url()));
        if (jaCadastrado) {
            return;
        }
        chamar("criarWebhook", null, false, () -> restClient.post()
                .uri("/v3/webhooks")
                .header(HEADER_CHAVE, chavePai)
                .contentType(MediaType.APPLICATION_JSON)
                .body(webhook(EVENTOS_WEBHOOK_CONTA_PAI))
                .retrieve()
                .toBodilessEntity());
    }

    // ---- Somente Sandbox ----

    @Override
    public void sandboxAprovarConta(CredencialProvedor credencialDaConta) {
        exigirSandbox();
        String chave = exigir(credencialDaConta);
        chamar("sandboxAprovarConta", null, false, () -> restClient.post()
                .uri("/v3/sandbox/myAccount/approve")
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void sandboxConfirmarPagamento(CredencialProvedor credencial, String idCobrancaExterna) {
        exigirSandbox();
        String chave = exigir(credencial);
        chamar("sandboxConfirmarPagamento", idCobrancaExterna, false, () -> restClient.post()
                .uri("/v3/sandbox/payment/{id}/confirm", idCobrancaExterna)
                .header(HEADER_CHAVE, chave)
                .retrieve()
                .toBodilessEntity());
    }

    // ---- HTTP ----

    private <T> T corpo(String operacao, String id, Supplier<ResponseEntity<T>> chamada) {
        return corpo(operacao, id, false, chamada);
    }

    private <T> T corpo(String operacao, String id, boolean recusaInconclusiva, Supplier<ResponseEntity<T>> chamada) {
        T corpo = chamar(operacao, id, recusaInconclusiva, chamada).getBody();
        if (corpo == null) {
            throw new ProvedorIndisponivelException("Resposta vazia do provedor de pagamento");
        }
        return corpo;
    }

    /**
     * Executa a chamada, registra o resultado no log e traduz o erro HTTP.
     * {@code recusaInconclusiva} faz o 400 virar inconclusivo, nas operacoes
     * em que o Asaas usa 400 para falha temporaria.
     */
    private <T> ResponseEntity<T> chamar(String operacao, String id, boolean recusaInconclusiva,
            Supplier<ResponseEntity<T>> chamada) {
        long inicio = System.nanoTime();
        try {
            ResponseEntity<T> resposta = chamada.get();
            registrar(operacao, id, String.valueOf(resposta.getStatusCode().value()), inicio);
            return resposta;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            registrar(operacao, id, String.valueOf(status), inicio);
            throw traduzir(status, e.getResponseBodyAsString(), recusaInconclusiva);
        } catch (ResourceAccessException e) {
            registrar(operacao, id, "sem resposta", inicio);
            throw new ProvedorIndisponivelException("Provedor de pagamento nao respondeu", e);
        }
    }

    private RuntimeException traduzir(int status, String corpoErro, boolean recusaInconclusiva) {
        if (status == 401 || status == 403) {
            return new ProvedorNaoConfiguradoException("Chave do provedor de pagamento invalida ou sem permissao");
        }
        if (status == 404) {
            return new NaoEncontradaNoProvedorException();
        }
        if (status == 429 || status >= 500) {
            return new ProvedorIndisponivelException("Provedor de pagamento indisponivel (HTTP " + status + ")");
        }
        String mensagem = mensagemDoErro(corpoErro);
        return recusaInconclusiva ? new ProvedorIndisponivelException(mensagem) : new ProvedorRecusouException(mensagem);
    }

    private void registrar(String operacao, String id, String status, long inicio) {
        long duracao = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        log.info("Asaas {} id={} status={} duracaoMs={}", operacao, id == null ? "-" : id, status, duracao);
    }

    /** {@code errors[].description} do Asaas, sem CPF/CNPJ nem e-mail, com no maximo 500 caracteres. */
    private String mensagemDoErro(String corpoErro) {
        String mensagem;
        try {
            ErroAsaas erro = json.readValue(corpoErro, ErroAsaas.class);
            mensagem = erro.errors() == null ? null
                    : erro.errors().stream()
                            .map(ErroAsaas.Item::description)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining("; "));
        } catch (JacksonException | IllegalArgumentException e) {
            mensagem = null;
        }
        if (mensagem == null || mensagem.isBlank()) {
            return "O provedor de pagamento recusou o pedido";
        }
        return sanear(mensagem);
    }

    static String sanear(String mensagem) {
        String semDados = EMAIL.matcher(DOCUMENTO.matcher(mensagem).replaceAll("***")).replaceAll("***");
        return semDados.length() <= MAX_MENSAGEM ? semDados : semDados.substring(0, MAX_MENSAGEM);
    }

    private String chaveDaContaPai() {
        if (!properties.configurado()) {
            throw new ProvedorNaoConfiguradoException("ASAAS_API_KEY nao configurada");
        }
        return properties.apiKey();
    }

    private static String exigir(CredencialProvedor credencial) {
        if (credencial == null || !credencial.preenchida()) {
            throw new ProvedorNaoConfiguradoException("Conta sem credencial do provedor");
        }
        return credencial.valor();
    }

    private void exigirSandbox() {
        if (!properties.sandbox()) {
            throw new IllegalStateException("Operacao disponivel so no Sandbox do Asaas");
        }
    }

    /** Sem URL, e-mail ou token o webhook nao e cadastrado (o Asaas exige e-mail, achado A2). */
    private List<WebhookAsaas> webhooks() {
        if (!properties.webhookConfigurado()) {
            log.warn("Webhook do Asaas nao configurado: conta filha criada sem webhook");
            return null;
        }
        return List.of(webhook(EVENTOS_WEBHOOK));
    }

    private WebhookAsaas webhook(List<String> eventos) {
        return new WebhookAsaas("Eduka", properties.webhookUrl(), properties.webhookEmail(), true, false,
                properties.webhookToken(), "SEQUENTIALLY", eventos);
    }

    // ---- Mapeamentos (so aqui) ----

    static CobrancaProvedor paraCobranca(CobrancaAsaas c) {
        StatusCobrancaProvedor status = statusDaCobranca(c);
        boolean paga = status == StatusCobrancaProvedor.CONFIRMADA || status == StatusCobrancaProvedor.RECEBIDA;
        FormaPagamento forma = formaDaCobranca(c.billingType());
        return new CobrancaProvedor(
                c.id(),
                c.externalReference(),
                status,
                forma,
                c.value(),
                paga ? c.value() : null,
                c.netValue(),
                primeiroNaoNulo(c.clientPaymentDate(), c.paymentDate(), c.confirmedDate()),
                c.creditDate(),
                idTransacao(c, forma),
                c.transactionReceiptUrl());
    }

    static StatusCobrancaProvedor statusDaCobranca(CobrancaAsaas c) {
        if (c.removida()) {
            return StatusCobrancaProvedor.REMOVIDA;
        }
        if (c.status() == null) {
            return StatusCobrancaProvedor.OUTRO;
        }
        return switch (c.status()) {
            case "PENDING" -> StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO;
            case "OVERDUE" -> StatusCobrancaProvedor.VENCIDA;
            case "CONFIRMED" -> StatusCobrancaProvedor.CONFIRMADA;
            case "RECEIVED", "RECEIVED_IN_CASH" -> StatusCobrancaProvedor.RECEBIDA;
            default -> StatusCobrancaProvedor.OUTRO;
        };
    }

    private static FormaPagamento formaDaCobranca(String billingType) {
        if ("PIX".equals(billingType)) {
            return FormaPagamento.PIX;
        }
        if ("BOLETO".equals(billingType)) {
            return FormaPagamento.BOLETO;
        }
        throw new IllegalStateException("Forma de pagamento inesperada no provedor: " + billingType);
    }

    private static String idTransacao(CobrancaAsaas c, FormaPagamento forma) {
        String id = forma == FormaPagamento.PIX ? c.pixTransaction() : c.nossoNumero();
        return id != null ? id : c.id();
    }

    /**
     * Total devolvido segundo o aviso de estorno: a soma dos {@code refunds}
     * concluidos. Sem nenhum, o estorno total vale o valor da cobranca; o
     * parcial nao diz quanto foi, e fica nulo.
     */
    static BigDecimal valorEstornado(EventoAsaas evento) {
        if (tipoDoEvento(evento.event()) != TipoEventoProvedor.ESTORNO || evento.payment() == null) {
            return null;
        }
        List<CobrancaAsaas.EstornoAsaas> estornos = evento.payment().refunds() == null ? List.of()
                : evento.payment().refunds();
        List<BigDecimal> concluidos = estornos.stream()
                .filter(CobrancaAsaas.EstornoAsaas::concluido)
                .map(CobrancaAsaas.EstornoAsaas::value)
                .filter(Objects::nonNull)
                .toList();
        if (!concluidos.isEmpty()) {
            return concluidos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return "PAYMENT_REFUNDED".equals(evento.event()) ? evento.payment().value() : null;
    }

    static TipoEventoProvedor tipoDoEvento(String evento) {
        if (evento == null) {
            return TipoEventoProvedor.NAO_TRATADO;
        }
        if (evento.startsWith("ACCOUNT_STATUS_")) {
            return TipoEventoProvedor.SITUACAO_CONTA_ALTERADA;
        }
        return switch (evento) {
            case "PAYMENT_CREATED" -> TipoEventoProvedor.COBRANCA_CRIADA;
            case "PAYMENT_CONFIRMED" -> TipoEventoProvedor.PAGAMENTO_CONFIRMADO;
            case "PAYMENT_RECEIVED" -> TipoEventoProvedor.PAGAMENTO_RECEBIDO;
            case "PAYMENT_DELETED" -> TipoEventoProvedor.COBRANCA_REMOVIDA;
            // Estorno em andamento ou negado ainda nao devolveu o dinheiro: nao e estorno.
            case "PAYMENT_REFUNDED", "PAYMENT_PARTIALLY_REFUNDED" -> TipoEventoProvedor.ESTORNO;
            case "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE",
                    "PAYMENT_AWAITING_CHARGEBACK_REVERSAL" -> TipoEventoProvedor.CONTESTACAO;
            default -> TipoEventoProvedor.NAO_TRATADO;
        };
    }

    static SituacaoContaProvedor paraSituacao(SituacaoContaAsaas s) {
        return new SituacaoContaProvedor(situacao(s.general()), situacao(s.documentation()),
                situacao(s.commercialInfo()), situacao(s.bankAccountInfo()));
    }

    /** Valor desconhecido conta como pendente: nunca libera recebimento por engano. */
    static SituacaoCadastral situacao(String valor) {
        if (valor == null) {
            return null;
        }
        return switch (valor) {
            case "AWAITING_APPROVAL" -> SituacaoCadastral.EM_ANALISE;
            case "APPROVED" -> SituacaoCadastral.APROVADO;
            case "REJECTED" -> SituacaoCadastral.REJEITADO;
            default -> SituacaoCadastral.PENDENTE;
        };
    }

    /** Remove dados pessoais, bancarios e de cartao do payload, em qualquer nivel. */
    private static JsonNode sanear(JsonNode raiz) {
        JsonNode copia = raiz.deepCopy();
        removerSensiveis(copia);
        return copia;
    }

    private static void removerSensiveis(JsonNode no) {
        if (no instanceof ObjectNode objeto) {
            List<String> remover = new ArrayList<>();
            for (String nome : objeto.propertyNames()) {
                if (CHAVES_SENSIVEIS.contains(nome) || nome.startsWith(PREFIXO_CARTAO)) {
                    remover.add(nome);
                }
            }
            objeto.remove(remover);
        }
        for (JsonNode filho : no.values()) {
            removerSensiveis(filho);
        }
    }

    private static LocalDate dataDoEvento(String dataHora) {
        if (dataHora == null || dataHora.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(dataHora.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static OffsetDateTime dataHora(String valor) {
        if (valor == null) {
            return null;
        }
        return LocalDateTime.parse(valor, DATA_HORA_ASAAS).atZone(FUSO_ASAAS).toOffsetDateTime();
    }

    @SafeVarargs
    private static <T> T primeiroNaoNulo(T... valores) {
        for (T valor : valores) {
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    /** 404 do Asaas: vira sucesso em {@code removerCobranca} e recusa nas consultas. */
    private static class NaoEncontradaNoProvedorException extends ProvedorRecusouException {

        NaoEncontradaNoProvedorException() {
            super("Registro nao encontrado no provedor de pagamento");
        }
    }
}
