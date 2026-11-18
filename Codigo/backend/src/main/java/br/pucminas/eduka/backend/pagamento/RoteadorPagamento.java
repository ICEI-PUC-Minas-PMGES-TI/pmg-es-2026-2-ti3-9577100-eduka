package br.pucminas.eduka.backend.pagamento;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.financeiro.ClienteProvedor;
import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.ContaProvedorCliente;
import br.pucminas.eduka.backend.domain.financeiro.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.financeiro.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.financeiro.BoletoResponse;
import br.pucminas.eduka.backend.dto.financeiro.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.financeiro.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.financeiro.HabilitacaoRecebimentos;
import br.pucminas.eduka.backend.dto.financeiro.PixResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.financeiro.BoletoNaoGeradoException;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaJaPagaException;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaNaoPagavelException;
import br.pucminas.eduka.backend.exception.financeiro.ComprovanteIndisponivelException;
import br.pucminas.eduka.backend.exception.financeiro.FormaPagamentoInvalidaException;
import br.pucminas.eduka.backend.exception.financeiro.PagadorSemCpfException;
import br.pucminas.eduka.backend.exception.financeiro.PagamentoEmProcessamentoException;
import br.pucminas.eduka.backend.exception.financeiro.RecebimentosIndisponiveisException;
import br.pucminas.eduka.backend.pagamento.tipos.BoletoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosPagador;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.NovaCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.PixProvedor;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.ClienteProvedorRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.validation.CpfValidator;

/**
 * Pagar uma cobranca escolar (Fluxo B, spec §8.2), por tras do
 * {@link PaymentRoutingService}.
 *
 * <p>
 * Tres passos (spec §4.3): reservar numa transacao curta, levando a cobranca
 * para EMITINDO; chamar o provedor sem transacao; gravar o resultado em outra
 * transacao curta. A trava otimista da cobranca garante que dois cliques ao
 * mesmo tempo criam uma cobranca so no provedor (R14). Cobranca ja EMITIDA e
 * reaproveitada: trocar a forma altera a mesma cobranca no provedor.
 * </p>
 *
 * <p>
 * Tambem remove no provedor a cobranca que a escola vai cancelar (spec §8.5)
 * e monta o comprovante do pagamento online (R28 a R30).
 * </p>
 */
@Component
class RoteadorPagamento {

    private static final Logger log = LoggerFactory.getLogger(RoteadorPagamento.class);

    /** O vencimento segue o calendario da escola, nao o de UTC. */
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    /** Referencia externa da cobranca escolar no provedor. */
    static final String PREFIXO_COBRANCA = "EDK-B-";

    /** Referencia externa do pagador (pessoa) na conta filha. */
    static final String PREFIXO_PESSOA = "EDK-PES-";

    private static final int TAMANHO_MAXIMO_ERRO = 500;

    private final CobrancaRepository cobrancaRepository;
    private final AlunoRepository alunoRepository;
    private final AlunoResponsavelRepository alunoResponsavelRepository;
    private final PessoaRepository pessoaRepository;
    private final ContaRecebimentoEscolaRepository contaRepository;
    private final ClienteProvedorRepository clienteRepository;
    private final PagamentoCobrancaRepository pagamentoRepository;
    private final EscolaRepository escolaRepository;
    private final RoteadorConta roteadorConta;
    private final RoteadorEventos eventos;
    private final IsolamentoEscola isolamentoEscola;
    private final PiiCryptoService piiCryptoService;
    private final ChamadorProvedor provedor;
    private final TransactionTemplate transacao;
    private final Clock clock;

    RoteadorPagamento(CobrancaRepository cobrancaRepository, AlunoRepository alunoRepository,
            AlunoResponsavelRepository alunoResponsavelRepository, PessoaRepository pessoaRepository,
            ContaRecebimentoEscolaRepository contaRepository, ClienteProvedorRepository clienteRepository,
            PagamentoCobrancaRepository pagamentoRepository, EscolaRepository escolaRepository,
            RoteadorConta roteadorConta, RoteadorEventos eventos, IsolamentoEscola isolamentoEscola,
            PiiCryptoService piiCryptoService, ChamadorProvedor provedor,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.alunoRepository = alunoRepository;
        this.alunoResponsavelRepository = alunoResponsavelRepository;
        this.pessoaRepository = pessoaRepository;
        this.contaRepository = contaRepository;
        this.clienteRepository = clienteRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.escolaRepository = escolaRepository;
        this.roteadorConta = roteadorConta;
        this.eventos = eventos;
        this.isolamentoEscola = isolamentoEscola;
        this.piiCryptoService = piiCryptoService;
        this.provedor = provedor;
        this.transacao = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Devolve os dados para pagar a cobranca do aluno do token com a forma
     * escolhida, criando a cobranca no provedor no primeiro clique.
     *
     * @return PRONTO com o QR Code ou a linha digitavel; PROCESSANDO quando o
     *         provedor nao respondeu a tempo na criacao (a cobranca fica
     *         EMITINDO)
     */
    DadosPagamentoResponse pagarCobranca(UUID cobrancaId, FormaPagamento forma) {
        if (forma == null) {
            throw new FormaPagamentoInvalidaException();
        }
        Reserva reserva = reservar(cobrancaId, forma);
        // R8: a credencial so e decifrada aqui, na hora da chamada.
        CredencialProvedor credencial = new CredencialProvedor(piiCryptoService.decifrar(reserva.credencialCifrada()));

        String idCobrancaExterna;
        if (reserva.emitir()) {
            idCobrancaExterna = emitir(reserva, forma, credencial);
            if (idCobrancaExterna == null) {
                return DadosPagamentoResponse.emProcessamento(forma, reserva.valor(), reserva.vencimentoEnviado())
                        .comRecebedor(nomeDaEscola(reserva.escolaId()));
            }
        } else {
            idCobrancaExterna = reserva.idCobrancaExterna();
            // R16: vencida, a cobranca no provedor pode estar com vencimento passado.
            if (forma != reserva.formaAtual() || reserva.vencida()) {
                alterarFormaEVencimento(reserva, forma, credencial);
            }
        }
        return dadosDePagamento(reserva, forma, credencial, idCobrancaExterna)
                .comRecebedor(nomeDaEscola(reserva.escolaId()));
    }

    /**
     * PDF do boleto da cobranca do aluno do token. Le a cobranca numa
     * transacao curta e chama o provedor sem transacao, como o pagar.
     *
     * @throws BoletoNaoGeradoException    se a cobranca ainda nao foi emitida
     *                                     como boleto
     * @throws CobrancaNaoPagavelException se a cobranca foi cancelada
     */
    byte[] baixarBoleto(UUID cobrancaId) {
        BoletoEmitido boleto = transacao.execute(status -> boletoEmitido(cobrancaId));
        CredencialProvedor credencial = new CredencialProvedor(piiCryptoService.decifrar(boleto.credencialCifrada()));
        return provedor.chamar(p -> p.baixarBoletoPdf(credencial, boleto.idCobrancaExterna()));
    }

    private BoletoEmitido boletoEmitido(UUID cobrancaId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        UUID escolaId = usuario.escolaId();
        // R2: cobranca de outro aluno ou de outra escola responde 404, como se nao existisse.
        Aluno aluno = alunoRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(RoteadorPagamento::cobrancaNaoEncontrada);
        Cobranca cobranca = cobrancaRepository.findByIdAndEscolaId(cobrancaId, escolaId)
                .filter(c -> c.getAluno().getId().equals(aluno.getId()))
                .orElseThrow(RoteadorPagamento::cobrancaNaoEncontrada);
        // Cancelada nao deve ser paga; paga continua baixavel, como registro.
        if (cobranca.getStatus() == StatusCobranca.CANCELADA) {
            throw new CobrancaNaoPagavelException();
        }
        if (cobranca.getSituacaoProvedor() != SituacaoProvedor.EMITIDA
                || cobranca.getFormaPagamento() != FormaPagamento.BOLETO) {
            throw new BoletoNaoGeradoException();
        }
        ContaRecebimentoEscola conta = contaRepository.findByEscolaId(escolaId)
                .orElseThrow(() -> new IllegalStateException("Cobranca emitida sem conta de recebimento"));
        return new BoletoEmitido(cobranca.getIdCobrancaExterna(), conta.getCredencialCifrada());
    }

    private record BoletoEmitido(String idCobrancaExterna, byte[] credencialCifrada) {
    }

    // ---- Comprovante (R28 a R30) ----

    /**
     * Comprovante do pagamento online da cobranca, montado pelo Eduka, nunca a
     * pagina do provedor. O ALUNO ve so os das proprias cobrancas; MASTER e
     * FINANCEIRO, os da escola do token. O beneficiario e a escola; o pagador
     * segue o R18 e sai com o CPF mascarado.
     *
     * @throws ComprovanteIndisponivelException sem pagamento online registrado
     *                                          (a baixa manual nao tem)
     */
    ComprovanteResponse comprovante(UUID cobrancaId) {
        return transacao.execute(status -> {
            UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
            UUID escolaId = usuario.escolaId();
            Cobranca cobranca = cobrancaRepository.findByIdAndEscolaId(cobrancaId, escolaId)
                    .orElseThrow(RoteadorPagamento::cobrancaNaoEncontrada);
            // R2: cobranca de outro aluno responde 404, como se nao existisse.
            if (usuario.perfilUsuario() == PerfilUsuario.ALUNO && alunoRepository
                    .findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                    .filter(aluno -> aluno.getId().equals(cobranca.getAluno().getId()))
                    .isEmpty()) {
                throw cobrancaNaoEncontrada();
            }
            PagamentoCobranca pagamento = cobranca.getStatus() != StatusCobranca.PAGA ? null
                    : pagamentoRepository.findByEscolaIdAndCobrancaId(escolaId, cobrancaId).orElse(null);
            if (pagamento == null) {
                throw new ComprovanteIndisponivelException();
            }
            Escola escola = escolaRepository.findById(escolaId)
                    .orElseThrow(() -> new IllegalStateException("Cobranca sem escola"));
            Optional<Pessoa> pagador = pessoaPagadora(cobranca, cobranca.getAluno(), escolaId);
            return new ComprovanteResponse(cobranca.getId(), escola.getNome(),
                    documentoDoBeneficiario(escola.getCnpjCpf()), pagador.map(Pessoa::getNome).orElse(null),
                    pagador.map(p -> RoteadorFatura.mascarar(p.getCpf())).orElse(null), cobranca.getDescricao(),
                    cobranca.getDataVencimento(), pagamento.getValorPago(), pagamento.getDataPagamento(),
                    pagamento.getFormaPagamento(), pagamento.getIdTransacaoExterna(), pagamento.getValorEstornado());
        });
    }

    /** CNPJ da escola sai inteiro, como em qualquer recibo; escola cadastrada com CPF sai mascarada (R30). */
    static String documentoDoBeneficiario(String documento) {
        String digitos = documento == null ? "" : documento.replaceAll("\\D", "");
        if (digitos.length() == 14) {
            return digitos.substring(0, 2) + "." + digitos.substring(2, 5) + "." + digitos.substring(5, 8) + "/"
                    + digitos.substring(8, 12) + "-" + digitos.substring(12);
        }
        return RoteadorFatura.mascarar(documento);
    }

    // ---- Cancelar (spec §8.5) ----

    /**
     * Remove no provedor a cobranca ja emitida, antes de a escola cancelar no
     * Eduka. Chamado sem transacao aberta, com a cobranca lida pelo
     * {@code CobrancaService}. Nunca emitida (ou com a emissao recusada): nada
     * a fazer no provedor.
     *
     * @throws CobrancaJaPagaException          o provedor ja recebeu; o
     *                                          pagamento fica registrado
     * @throws PagamentoEmProcessamentoException a cobranca esta sendo gerada
     */
    void cancelarNoProvedor(Cobranca cobranca) {
        switch (cobranca.getSituacaoProvedor()) {
            case NAO_EMITIDA, FALHA_EMISSAO -> {
                return;
            }
            case EMITINDO -> throw new PagamentoEmProcessamentoException();
            case EMITIDA -> {
                // Segue para o provedor.
            }
        }
        ContaRecebimentoEscola conta = contaRepository.findByEscolaId(cobranca.getEscolaId())
                .orElseThrow(() -> new IllegalStateException("Cobranca emitida sem conta de recebimento"));
        CredencialProvedor credencial = new CredencialProvedor(piiCryptoService.decifrar(conta.getCredencialCifrada()));
        String idExterno = cobranca.getIdCobrancaExterna();

        CobrancaProvedor noProvedor = provedor.chamar(p -> p.consultarCobranca(credencial, idExterno));
        if (RoteadorFatura.pago(noProvedor)) {
            eventos.registrarPagamentoEncontrado(cobranca.getId(), noProvedor);
            throw new CobrancaJaPagaException();
        }
        provedor.executar(p -> p.removerCobranca(credencial, idExterno));
    }

    // ---- Passo 1: reservar [T] ----

    private Reserva reservar(UUID cobrancaId, FormaPagamento forma) {
        try {
            return transacao.execute(status -> reservarNaTransacao(cobrancaId, forma));
        } catch (OptimisticLockingFailureException e) {
            // Outro pedido mudou a cobranca entre a leitura e a gravacao.
            throw new PagamentoEmProcessamentoException();
        }
    }

    private Reserva reservarNaTransacao(UUID cobrancaId, FormaPagamento forma) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        UUID escolaId = usuario.escolaId();
        // R2: cobranca de outro aluno ou de outra escola responde 404, como se nao existisse.
        Aluno aluno = alunoRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(RoteadorPagamento::cobrancaNaoEncontrada);
        Cobranca cobranca = cobrancaRepository.findByIdAndEscolaId(cobrancaId, escolaId)
                .filter(c -> c.getAluno().getId().equals(aluno.getId()))
                .orElseThrow(RoteadorPagamento::cobrancaNaoEncontrada);

        // R12
        if (!cobranca.getStatus().emAberto()) {
            throw new CobrancaNaoPagavelException();
        }
        HabilitacaoRecebimentos habilitacao = roteadorConta.habilitacao(escolaId);
        if (!habilitacao.habilitados()) {
            throw new RecebimentosIndisponiveisException(habilitacao.motivo());
        }
        ContaRecebimentoEscola conta = contaRepository.findByEscolaId(escolaId)
                .orElseThrow(() -> new IllegalStateException("Escola habilitada sem conta de recebimento"));

        LocalDate hoje = hoje();
        LocalDate vencimentoEnviado = cobranca.getDataVencimento().isBefore(hoje) ? hoje : cobranca.getDataVencimento();
        boolean vencida = cobranca.getDataVencimento().isBefore(hoje);

        return switch (cobranca.getSituacaoProvedor()) {
            case EMITINDO -> throw new PagamentoEmProcessamentoException();
            case EMITIDA -> new Reserva(cobranca.getId(), escolaId, cobranca.getValor(), cobranca.getDescricao(),
                    vencimentoEnviado, vencida, conta.getCredencialCifrada(), false, cobranca.getIdCobrancaExterna(),
                    cobranca.getFormaPagamento(), null, null);
            case NAO_EMITIDA, FALHA_EMISSAO -> {
                // O pagador e conferido antes de travar: sem CPF, a cobranca nao fica EMITINDO.
                DadosPagador pagador = pagadorDa(cobranca, aluno, escolaId);
                String idCliente = clienteRepository
                        .findByEscolaIdAndContaAndReferenciaExterna(escolaId, ContaProvedorCliente.FILHA,
                                pagador.referenciaExterna())
                        .map(ClienteProvedor::getIdClienteExterno)
                        .orElse(null);

                // R14: transicao atomica pela trava otimista.
                cobranca.setSituacaoProvedor(SituacaoProvedor.EMITINDO);
                cobranca.setEmissaoIniciadaEm(clock.instant());
                cobranca.setFormaPagamento(forma);
                cobrancaRepository.saveAndFlush(cobranca);
                yield new Reserva(cobranca.getId(), escolaId, cobranca.getValor(), cobranca.getDescricao(),
                        vencimentoEnviado, vencida, conta.getCredencialCifrada(), true, null, forma, pagador,
                        idCliente);
            }
        };
    }

    /**
     * R18: o responsavel informado na cobranca; senao o responsavel financeiro
     * do aluno; senao o proprio aluno. CPF valido e obrigatorio.
     */
    private DadosPagador pagadorDa(Cobranca cobranca, Aluno aluno, UUID escolaId) {
        Pessoa pessoa = pessoaPagadora(cobranca, aluno, escolaId).orElseThrow(PagadorSemCpfException::new);
        if (pessoa.getCpf() == null || !new CpfValidator().isValid(pessoa.getCpf(), null)) {
            throw new PagadorSemCpfException();
        }
        return new DadosPagador(PREFIXO_PESSOA + pessoa.getId(), pessoa.getNome(), pessoa.getCpf(), pessoa.getEmail());
    }

    /** Quem paga pela regra do R18, sem conferir o CPF. */
    private Optional<Pessoa> pessoaPagadora(Cobranca cobranca, Aluno aluno, UUID escolaId) {
        UUID pessoaId = cobranca.getResponsavelId() != null
                ? cobranca.getResponsavelId()
                : alunoResponsavelRepository.findById_AlunoIdAndFinanceiroTrue(aluno.getId())
                        .map(vinculo -> vinculo.getId().pessoaId())
                        .orElse(aluno.getPessoaId());
        return pessoaRepository.findByIdAndEscolaId(pessoaId, escolaId);
    }

    // ---- Passos 2 e 3: emitir [HTTP] e gravar [T] ----

    /** @return id da cobranca no provedor, ou {@code null} se a criacao ficou inconclusiva */
    private String emitir(Reserva reserva, FormaPagamento forma, CredencialProvedor credencial) {
        String idCliente;
        try {
            idCliente = reserva.idClienteExterno() != null
                    ? reserva.idClienteExterno()
                    : cadastrarPagador(reserva, credencial);
        } catch (ProvedorRecusouException | ProvedorIndisponivelException | ProvedorNaoConfiguradoException e) {
            // A cobranca ainda nao foi pedida: nada a reconciliar, o proximo clique tenta de novo.
            registrarFalha(reserva, mensagemDaFalha(e));
            throw e;
        }

        NovaCobrancaProvedor nova = new NovaCobrancaProvedor(idCliente, forma, reserva.valor(),
                reserva.vencimentoEnviado(), reserva.descricao(), PREFIXO_COBRANCA + reserva.cobrancaId());
        CobrancaProvedor criada;
        try {
            criada = provedor.chamar(p -> p.criarCobranca(credencial, nova));
        } catch (ProvedorIndisponivelException e) {
            // R15: inconclusivo. Fica EMITINDO; a reconciliacao procura pela referencia antes de recriar.
            log.warn("Criacao da cobranca {} no provedor ficou inconclusiva", reserva.cobrancaId());
            return null;
        } catch (ProvedorRecusouException | ProvedorNaoConfiguradoException e) {
            registrarFalha(reserva, mensagemDaFalha(e));
            throw e;
        }

        transacao.executeWithoutResult(status -> {
            Cobranca cobranca = recarregar(reserva);
            cobranca.setSituacaoProvedor(SituacaoProvedor.EMITIDA);
            cobranca.setIdCobrancaExterna(criada.idCobrancaExterna());
            cobranca.setEmitidaEm(clock.instant());
            cobranca.setUltimoErroProvedor(null);
            cobrancaRepository.saveAndFlush(cobranca);
        });
        return criada.idCobrancaExterna();
    }

    /** R19: um cliente por pagador na conta filha, guardado para nao cadastrar de novo. */
    private String cadastrarPagador(Reserva reserva, CredencialProvedor credencial) {
        String idCliente = provedor.chamar(p -> p.obterOuCriarCliente(credencial, reserva.pagador()));
        try {
            transacao.executeWithoutResult(status -> clienteRepository.saveAndFlush(ClienteProvedor.builder()
                    .escolaId(reserva.escolaId())
                    .conta(ContaProvedorCliente.FILHA)
                    .referenciaExterna(reserva.pagador().referenciaExterna())
                    .idClienteExterno(idCliente)
                    .build()));
        } catch (DataIntegrityViolationException e) {
            // Outro pagamento do mesmo pagador gravou antes; o provedor achou o mesmo cliente pela referencia.
        }
        return idCliente;
    }

    private void registrarFalha(Reserva reserva, String mensagem) {
        transacao.executeWithoutResult(status -> {
            Cobranca cobranca = recarregar(reserva);
            if (cobranca.getSituacaoProvedor() == SituacaoProvedor.EMITINDO) {
                cobranca.setSituacaoProvedor(SituacaoProvedor.FALHA_EMISSAO);
                cobranca.setUltimoErroProvedor(mensagem);
                cobrancaRepository.saveAndFlush(cobranca);
            }
        });
    }

    // ---- Passo 4: trocar a forma [HTTP] e gravar [T] ----

    private void alterarFormaEVencimento(Reserva reserva, FormaPagamento forma, CredencialProvedor credencial) {
        provedor.chamar(p -> p.alterarFormaEVencimento(credencial, reserva.idCobrancaExterna(), forma,
                reserva.vencimentoEnviado()));
        transacao.executeWithoutResult(status -> {
            Cobranca cobranca = recarregar(reserva);
            cobranca.setFormaPagamento(forma);
            cobrancaRepository.saveAndFlush(cobranca);
        });
    }

    // ---- Passo 5: dados de pagamento [HTTP] ----

    /** R17: so o QR Code em Base64, o copia e cola ou a linha digitavel; nenhuma URL do provedor. */
    private DadosPagamentoResponse dadosDePagamento(Reserva reserva, FormaPagamento forma,
            CredencialProvedor credencial, String idCobrancaExterna) {
        if (forma == FormaPagamento.PIX) {
            PixProvedor pix = provedor.chamar(p -> p.obterPix(credencial, idCobrancaExterna));
            return DadosPagamentoResponse.comPix(reserva.valor(), reserva.vencimentoEnviado(),
                    new PixResponse(pix.qrCodeBase64(), pix.copiaECola(), pix.validade()));
        }
        BoletoProvedor boleto = provedor.chamar(p -> p.obterLinhaDigitavel(credencial, idCobrancaExterna));
        return DadosPagamentoResponse.comBoleto(reserva.valor(), reserva.vencimentoEnviado(),
                new BoletoResponse(boleto.linhaDigitavel(), boleto.codigoBarras()));
    }

    /** A escola recebe o dinheiro da cobranca: a tela mostra para o pagador conferir o destino. */
    private String nomeDaEscola(UUID escolaId) {
        return escolaRepository.findById(escolaId).map(Escola::getNome).orElse(null);
    }

    private Cobranca recarregar(Reserva reserva) {
        return cobrancaRepository.findByIdAndEscolaId(reserva.cobrancaId(), reserva.escolaId())
                .orElseThrow(RoteadorPagamento::cobrancaNaoEncontrada);
    }

    /** A recusa ja vem saneada pelo adaptador; as outras falhas tem mensagem fixa. */
    private static String mensagemDaFalha(RuntimeException e) {
        String mensagem = e instanceof ProvedorRecusouException
                ? e.getMessage()
                : e instanceof ProvedorNaoConfiguradoException
                        ? "Provedor de pagamento nao configurado"
                        : "Provedor de pagamento indisponivel";
        if (mensagem == null) {
            return null;
        }
        return mensagem.length() <= TAMANHO_MAXIMO_ERRO ? mensagem : mensagem.substring(0, TAMANHO_MAXIMO_ERRO);
    }

    private static RecursoNaoEncontradoException cobrancaNaoEncontrada() {
        return new RecursoNaoEncontradoException("Cobranca nao encontrada");
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }

    /**
     * O que o passo 1 leu dentro da transacao e os passos seguintes usam fora
     * dela.
     *
     * @param emitir            true quando a cobranca acabou de ir para EMITINDO
     * @param idCobrancaExterna so quando ja estava EMITIDA
     * @param formaAtual        forma gravada antes deste clique
     * @param pagador           so quando {@code emitir}
     * @param idClienteExterno  cliente ja cadastrado no provedor; nulo se ainda nao
     */
    private record Reserva(UUID cobrancaId, UUID escolaId, BigDecimal valor, String descricao,
            LocalDate vencimentoEnviado, boolean vencida, byte[] credencialCifrada, boolean emitir,
            String idCobrancaExterna, FormaPagamento formaAtual, DadosPagador pagador, String idClienteExterno) {
    }
}
