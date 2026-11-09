package br.pucminas.eduka.backend.pagamento;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.ClienteProvedor;
import br.pucminas.eduka.backend.domain.ContaProvedorCliente;
import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.BoletoResponse;
import br.pucminas.eduka.backend.dto.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.LancarFaturaRequest;
import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.dto.PixResponse;
import br.pucminas.eduka.backend.exception.CobrancaJaPagaException;
import br.pucminas.eduka.backend.exception.CobrancaNaoPagavelException;
import br.pucminas.eduka.backend.exception.ComprovanteIndisponivelException;
import br.pucminas.eduka.backend.exception.FaturaDuplicadaException;
import br.pucminas.eduka.backend.exception.FormaPagamentoInvalidaException;
import br.pucminas.eduka.backend.exception.PagamentoEmProcessamentoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.pagamento.tipos.BoletoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosPagador;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.NovaCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.PixProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.repository.ClienteProvedorRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.service.AuditoriaService;

/**
 * Fatura da plataforma (Fluxo A, R26), por tras do {@link PaymentRoutingService}:
 * o Super Admin lanca, lista e cancela; o MASTER da escola lista e paga.
 *
 * <p>
 * A cobranca da fatura fica na conta-pai do Eduka. Cancelar fatura ja emitida
 * remove primeiro no provedor, fora de transacao (R25).
 * </p>
 */
@Component
class RoteadorFatura {

    private static final Logger log = LoggerFactory.getLogger(RoteadorFatura.class);

    /** O vencimento segue o calendario da escola, nao o de UTC. */
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    /** Referencia externa da fatura na conta-pai. */
    static final String PREFIXO_FATURA = "EDK-A-";

    /** Referencia externa da escola como cliente da conta-pai. */
    static final String PREFIXO_ESCOLA = "EDK-ESC-";

    private static final int TAMANHO_MAXIMO_ERRO = 500;

    /** No Fluxo A quem recebe e o Eduka (R30). */
    private static final String BENEFICIARIO = "Edúka";

    private final FaturaPlataformaRepository faturaRepository;
    private final EscolaRepository escolaRepository;
    private final ContaRecebimentoEscolaRepository contaRepository;
    private final ClienteProvedorRepository clienteRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final AuditoriaService auditoriaService;
    private final ChamadorProvedor provedor;
    private final TransactionTemplate transacao;
    private final Clock clock;

    RoteadorFatura(FaturaPlataformaRepository faturaRepository, EscolaRepository escolaRepository,
            ContaRecebimentoEscolaRepository contaRepository, ClienteProvedorRepository clienteRepository,
            IsolamentoEscola isolamentoEscola, AuditoriaService auditoriaService, ChamadorProvedor provedor,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.faturaRepository = faturaRepository;
        this.escolaRepository = escolaRepository;
        this.contaRepository = contaRepository;
        this.clienteRepository = clienteRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.auditoriaService = auditoriaService;
        this.provedor = provedor;
        this.transacao = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ---- Super Admin ----

    /**
     * R26: escola ativa, competencia unica (fora as canceladas), vencimento a
     * partir de hoje.
     *
     * @throws FaturaDuplicadaException ja ha fatura nessa competencia
     */
    FaturaPlataformaResponse lancar(UUID escolaId, LancarFaturaRequest dados) {
        LocalDate competencia = competencia(dados.competencia());
        try {
            return transacao.execute(status -> {
                Escola escola = escolaAtiva(escolaId);
                if (dados.dataVencimento().isBefore(hoje())) {
                    throw new IllegalArgumentException("O vencimento nao pode ser anterior a hoje");
                }
                if (faturaRepository.existsByEscolaIdAndCompetenciaAndStatusNot(escolaId, competencia,
                        StatusCobranca.CANCELADA)) {
                    throw new FaturaDuplicadaException();
                }
                FaturaPlataforma fatura = faturaRepository.saveAndFlush(FaturaPlataforma.builder()
                        .escolaId(escolaId)
                        .competencia(competencia)
                        .descricao(dados.descricao().trim())
                        .valor(dados.valor())
                        .dataVencimento(dados.dataVencimento())
                        .criadaPor(isolamentoEscola.usuarioAtual().id())
                        .build());
                return FaturaPlataformaResponse.paraSuperAdmin(fatura, escola.getNome(), hoje());
            });
        } catch (DataIntegrityViolationException e) {
            // Dois lancamentos da mesma competencia ao mesmo tempo: o indice unico segura o segundo.
            throw new FaturaDuplicadaException();
        }
    }

    List<FaturaPlataformaResponse> listarDaEscola(UUID escolaId) {
        return transacao.execute(status -> {
            Escola escola = escola(escolaId);
            LocalDate hoje = hoje();
            return faturaRepository.findAllByEscolaIdOrderByCompetenciaDesc(escolaId).stream()
                    .map(f -> FaturaPlataformaResponse.paraSuperAdmin(f, escola.getNome(), hoje))
                    .toList();
        });
    }

    /** Faturas de todas as escolas (IU24), com o nome de cada uma. */
    List<FaturaPlataformaResponse> listarTodas() {
        return transacao.execute(status -> {
            LocalDate hoje = hoje();
            List<FaturaPlataforma> faturas = faturaRepository.findAllByOrderByDataVencimentoDescCompetenciaDesc();
            Map<UUID, String> nomes = escolaRepository
                    .findAllById(faturas.stream().map(FaturaPlataforma::getEscolaId).distinct().toList()).stream()
                    .collect(Collectors.toMap(Escola::getId, Escola::getNome));
            return faturas.stream()
                    .map(f -> FaturaPlataformaResponse.paraSuperAdmin(f, nomes.get(f.getEscolaId()), hoje))
                    .toList();
        });
    }

    PaginaResponse<FaturaPlataformaResponse> listarDaEscola(UUID escolaId, StatusCobranca situacao,
            Paginacao paginacao, Ordenacao.Faturas ordenarPor, Direction direcao) {
        return transacao.execute(status -> {
            Escola escola = escola(escolaId);
            LocalDate hoje = hoje();
            return PaginaResponse.de(pagina(escolaId, situacao, paginacao, ordenarPor, direcao),
                    f -> FaturaPlataformaResponse.paraSuperAdmin(f, escola.getNome(), hoje));
        });
    }

    /**
     * Cancela a fatura em aberto. Ja emitida: confere no provedor e, se o
     * pagamento ja entrou, registra o pagamento e recusa (R25).
     *
     * @throws CobrancaJaPagaException          paga aqui ou no provedor
     * @throws PagamentoEmProcessamentoException a cobranca esta sendo gerada
     */
    FaturaPlataformaResponse cancelar(UUID escolaId, UUID faturaId) {
        String idExterno = transacao.execute(status -> {
            FaturaPlataforma fatura = daEscola(escolaId, faturaId);
            if (fatura.getStatus() == StatusCobranca.PAGA) {
                throw new CobrancaJaPagaException();
            }
            if (fatura.getStatus() == StatusCobranca.CANCELADA) {
                return null;
            }
            return switch (fatura.getSituacaoProvedor()) {
                case EMITINDO -> throw new PagamentoEmProcessamentoException();
                case EMITIDA -> fatura.getIdCobrancaExterna();
                case NAO_EMITIDA, FALHA_EMISSAO -> {
                    marcarCancelada(fatura);
                    yield null;
                }
            };
        });

        if (idExterno != null) {
            CredencialProvedor credencial = provedor.chamar(PaymentProvider::credencialDaContaPai);
            CobrancaProvedor noProvedor = provedor.chamar(p -> p.consultarCobranca(credencial, idExterno));
            if (pago(noProvedor)) {
                transacao.executeWithoutResult(status -> {
                    FaturaPlataforma fatura = daEscola(escolaId, faturaId);
                    // O aviso do pagamento pode ter chegado e gravado antes.
                    if (fatura.getStatus() != StatusCobranca.PAGA) {
                        registrarPagamento(fatura, noProvedor, hoje(), clock.instant());
                        auditoriaService.registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO,
                                EntidadeAuditada.FATURA_PLATAFORMA, fatura.getId(), fatura.getEscolaId());
                    }
                });
                throw new CobrancaJaPagaException();
            }
            provedor.executar(p -> p.removerCobranca(credencial, idExterno));
            transacao.executeWithoutResult(status -> {
                FaturaPlataforma fatura = daEscola(escolaId, faturaId);
                if (fatura.getStatus().emAberto()) {
                    marcarCancelada(fatura);
                }
            });
        }
        return transacao.execute(status -> FaturaPlataformaResponse.paraSuperAdmin(daEscola(escolaId, faturaId),
                escola(escolaId).getNome(), hoje()));
    }

    // ---- MASTER da escola do token ----

    /**
     * Spec §8.2, versao da fatura: a cobranca vai para a conta-pai, o pagador
     * e a escola e a referencia e {@code EDK-A-<id>}. Nao depende da conta de
     * recebimento da escola.
     *
     * @return PRONTO com o QR Code ou a linha digitavel; PROCESSANDO quando o
     *         provedor nao respondeu a tempo na criacao
     */
    DadosPagamentoResponse pagar(UUID faturaId, FormaPagamento forma) {
        if (forma == null) {
            throw new FormaPagamentoInvalidaException();
        }
        Reserva reserva = reservar(faturaId, forma);
        CredencialProvedor credencial = provedor.chamar(PaymentProvider::credencialDaContaPai);

        String idCobrancaExterna;
        if (reserva.emitir()) {
            idCobrancaExterna = emitir(reserva, forma, credencial);
            if (idCobrancaExterna == null) {
                return DadosPagamentoResponse.emProcessamento(forma, reserva.valor(), reserva.vencimentoEnviado());
            }
        } else {
            idCobrancaExterna = reserva.idCobrancaExterna();
            if (forma != reserva.formaAtual() || reserva.vencida()) {
                provedor.chamar(p -> p.alterarFormaEVencimento(credencial, idCobrancaExterna, forma,
                        reserva.vencimentoEnviado()));
                transacao.executeWithoutResult(status -> {
                    FaturaPlataforma fatura = daEscola(reserva.escolaId(), reserva.faturaId());
                    fatura.setFormaPagamento(forma);
                    faturaRepository.saveAndFlush(fatura);
                });
            }
        }
        if (forma == FormaPagamento.PIX) {
            PixProvedor pix = provedor.chamar(p -> p.obterPix(credencial, idCobrancaExterna));
            return DadosPagamentoResponse.comPix(reserva.valor(), reserva.vencimentoEnviado(),
                    new PixResponse(pix.qrCodeBase64(), pix.copiaECola(), pix.validade()));
        }
        BoletoProvedor boleto = provedor.chamar(p -> p.obterLinhaDigitavel(credencial, idCobrancaExterna));
        return DadosPagamentoResponse.comBoleto(reserva.valor(), reserva.vencimentoEnviado(),
                new BoletoResponse(boleto.linhaDigitavel(), boleto.codigoBarras()));
    }

    private Reserva reservar(UUID faturaId, FormaPagamento forma) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        try {
            return transacao.execute(status -> {
                FaturaPlataforma fatura = daEscola(escolaId, faturaId);
                if (!fatura.getStatus().emAberto()) {
                    throw new CobrancaNaoPagavelException();
                }
                LocalDate hoje = hoje();
                boolean vencida = fatura.getDataVencimento().isBefore(hoje);
                LocalDate vencimentoEnviado = vencida ? hoje : fatura.getDataVencimento();
                return switch (fatura.getSituacaoProvedor()) {
                    case EMITINDO -> throw new PagamentoEmProcessamentoException();
                    case EMITIDA -> new Reserva(faturaId, escolaId, fatura.getValor(), fatura.getDescricao(),
                            vencimentoEnviado, vencida, false, fatura.getIdCobrancaExterna(),
                            fatura.getFormaPagamento(), null, null);
                    case NAO_EMITIDA, FALHA_EMISSAO -> {
                        DadosPagador pagador = pagadorDa(escolaId);
                        String idCliente = clienteRepository
                                .findByEscolaIdAndContaAndReferenciaExterna(escolaId, ContaProvedorCliente.PAI,
                                        pagador.referenciaExterna())
                                .map(ClienteProvedor::getIdClienteExterno)
                                .orElse(null);
                        // R14: dois cliques ao mesmo tempo, a trava otimista segura o segundo.
                        fatura.setSituacaoProvedor(SituacaoProvedor.EMITINDO);
                        fatura.setEmissaoIniciadaEm(clock.instant());
                        fatura.setFormaPagamento(forma);
                        faturaRepository.saveAndFlush(fatura);
                        yield new Reserva(faturaId, escolaId, fatura.getValor(), fatura.getDescricao(),
                                vencimentoEnviado, vencida, true, null, forma, pagador, idCliente);
                    }
                };
            });
        } catch (OptimisticLockingFailureException e) {
            throw new PagamentoEmProcessamentoException();
        }
    }

    /**
     * Pagador da fatura: a escola, com o CNPJ (ou CPF) do cadastro e o e-mail
     * financeiro da conta de recebimento, se houver, senao o da escola.
     */
    private DadosPagador pagadorDa(UUID escolaId) {
        Escola escola = escola(escolaId);
        String documento = escola.getCnpjCpf() == null ? "" : escola.getCnpjCpf().replaceAll("\\D", "");
        if (documento.length() != 11 && documento.length() != 14) {
            throw new IllegalArgumentException("A escola nao tem CNPJ cadastrado; atualize o cadastro para pagar");
        }
        String email = contaRepository.findByEscolaId(escolaId)
                .map(ContaRecebimentoEscola::getEmailFinanceiro)
                .orElse(escola.getEmail());
        return new DadosPagador(PREFIXO_ESCOLA + escolaId, escola.getNome(), documento, email);
    }

    /** @return id da cobranca na conta-pai, ou {@code null} se a criacao ficou inconclusiva */
    private String emitir(Reserva reserva, FormaPagamento forma, CredencialProvedor credencial) {
        String idCliente;
        try {
            idCliente = reserva.idClienteExterno() != null ? reserva.idClienteExterno()
                    : cadastrarPagador(reserva, credencial);
        } catch (ProvedorRecusouException | ProvedorIndisponivelException | ProvedorNaoConfiguradoException e) {
            registrarFalha(reserva, e);
            throw e;
        }
        NovaCobrancaProvedor nova = new NovaCobrancaProvedor(idCliente, forma, reserva.valor(),
                reserva.vencimentoEnviado(), reserva.descricao(), PREFIXO_FATURA + reserva.faturaId());
        CobrancaProvedor criada;
        try {
            criada = provedor.chamar(p -> p.criarCobranca(credencial, nova));
        } catch (ProvedorIndisponivelException e) {
            // R15: inconclusivo. Fica EMITINDO; o aviso PAYMENT_CREATED ou a reconciliacao resolvem.
            log.warn("Criacao da fatura {} no provedor ficou inconclusiva", reserva.faturaId());
            return null;
        } catch (ProvedorRecusouException | ProvedorNaoConfiguradoException e) {
            registrarFalha(reserva, e);
            throw e;
        }
        transacao.executeWithoutResult(status -> {
            FaturaPlataforma fatura = daEscola(reserva.escolaId(), reserva.faturaId());
            fatura.setSituacaoProvedor(SituacaoProvedor.EMITIDA);
            fatura.setIdCobrancaExterna(criada.idCobrancaExterna());
            fatura.setEmitidaEm(clock.instant());
            fatura.setUltimoErroProvedor(null);
            faturaRepository.saveAndFlush(fatura);
        });
        return criada.idCobrancaExterna();
    }

    /** R19: um cliente por escola na conta-pai, guardado para nao cadastrar de novo. */
    private String cadastrarPagador(Reserva reserva, CredencialProvedor credencial) {
        String idCliente = provedor.chamar(p -> p.obterOuCriarCliente(credencial, reserva.pagador()));
        try {
            transacao.executeWithoutResult(status -> clienteRepository.saveAndFlush(ClienteProvedor.builder()
                    .escolaId(reserva.escolaId())
                    .conta(ContaProvedorCliente.PAI)
                    .referenciaExterna(reserva.pagador().referenciaExterna())
                    .idClienteExterno(idCliente)
                    .build()));
        } catch (DataIntegrityViolationException e) {
            // Outro pagamento da mesma escola gravou antes; o provedor achou o mesmo cliente pela referencia.
        }
        return idCliente;
    }

    private void registrarFalha(Reserva reserva, RuntimeException erro) {
        String mensagem = erro instanceof ProvedorRecusouException ? erro.getMessage()
                : erro instanceof ProvedorNaoConfiguradoException ? "Provedor de pagamento nao configurado"
                        : "Provedor de pagamento indisponivel";
        transacao.executeWithoutResult(status -> {
            FaturaPlataforma fatura = daEscola(reserva.escolaId(), reserva.faturaId());
            if (fatura.getSituacaoProvedor() == SituacaoProvedor.EMITINDO) {
                fatura.setSituacaoProvedor(SituacaoProvedor.FALHA_EMISSAO);
                fatura.setUltimoErroProvedor(mensagem == null || mensagem.length() <= TAMANHO_MAXIMO_ERRO
                        ? mensagem : mensagem.substring(0, TAMANHO_MAXIMO_ERRO));
                faturaRepository.saveAndFlush(fatura);
            }
        });
    }

    /** Rotina diaria (spec §7.4): mesma regra de atraso da cobranca, no dia da escola. */
    int marcarVencidasComoEmAtraso() {
        return transacao.execute(status -> faturaRepository.marcarVencidasComoEmAtraso(hoje(), clock.instant()));
    }

    // ---- Comprovante (R28 a R30): Super Admin pela escola do caminho, MASTER pela do token ----

    /**
     * Comprovante montado pelo Eduka, nunca a pagina do provedor. O numero e o
     * id da fatura; o beneficiario e o Eduka.
     *
     * @throws ComprovanteIndisponivelException a fatura ainda nao foi paga
     */
    ComprovanteResponse comprovante(UUID escolaId, UUID faturaId) {
        return transacao.execute(status -> {
            FaturaPlataforma fatura = daEscola(escolaId, faturaId);
            if (fatura.getStatus() != StatusCobranca.PAGA || fatura.getValorPago() == null) {
                throw new ComprovanteIndisponivelException();
            }
            Escola escola = escola(escolaId);
            return new ComprovanteResponse(fatura.getId(), BENEFICIARIO, null, escola.getNome(),
                    mascarar(escola.getCnpjCpf()), fatura.getDescricao(), fatura.getDataVencimento(),
                    fatura.getValorPago(), fatura.getDataPagamento(), fatura.getFormaPagamento(),
                    fatura.getIdTransacaoExterna(), BigDecimal.ZERO);
        });
    }

    ComprovanteResponse comprovanteMinha(UUID faturaId) {
        return comprovante(isolamentoEscola.escolaIdAtual(), faturaId);
    }

    /** R30: so os digitos do meio aparecem ({@code ***.456.789-**}, {@code **.345.678/0001-**}). */
    static String mascarar(String documento) {
        String digitos = documento == null ? "" : documento.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            return "***." + digitos.substring(3, 6) + "." + digitos.substring(6, 9) + "-**";
        }
        if (digitos.length() == 14) {
            return "**." + digitos.substring(2, 5) + "." + digitos.substring(5, 8) + "/" + digitos.substring(8, 12)
                    + "-**";
        }
        return null;
    }

    List<FaturaPlataformaResponse> listarMinhas() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return transacao.execute(status -> {
            Escola escola = escola(escolaId);
            LocalDate hoje = hoje();
            return faturaRepository.findAllByEscolaIdOrderByCompetenciaDesc(escolaId).stream()
                    .map(f -> FaturaPlataformaResponse.paraEscola(f, escola.getNome(), hoje))
                    .toList();
        });
    }

    PaginaResponse<FaturaPlataformaResponse> listarMinhas(StatusCobranca situacao, Paginacao paginacao,
            Ordenacao.Faturas ordenarPor, Direction direcao) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return transacao.execute(status -> {
            Escola escola = escola(escolaId);
            LocalDate hoje = hoje();
            return PaginaResponse.de(pagina(escolaId, situacao, paginacao, ordenarPor, direcao),
                    f -> FaturaPlataformaResponse.paraEscola(f, escola.getNome(), hoje));
        });
    }

    /** Faturas da escola, opcionalmente so as de uma situacao. */
    private Page<FaturaPlataforma> pagina(UUID escolaId, StatusCobranca situacao, Paginacao paginacao,
            Ordenacao.Faturas ordenarPor, Direction direcao) {
        Pageable pageable = paginacao.comOrdem(
                Ordenacao.noBanco(Ordenacao.ou(direcao, ordenarPor.padrao), ordenarPor.propriedade));
        return situacao == null ? faturaRepository.findAllByEscolaId(escolaId, pageable)
                : faturaRepository.findAllByEscolaIdAndStatus(escolaId, situacao, pageable);
    }

    // ---- Apoio ----

    /**
     * Grava na fatura o pagamento que o provedor confirmou. Usado pelo
     * cancelamento (R25) e pelo webhook ({@code EDK-A-}).
     */
    static void registrarPagamento(FaturaPlataforma fatura, CobrancaProvedor pago, LocalDate hoje, Instant agora) {
        fatura.setStatus(StatusCobranca.PAGA);
        fatura.setSituacaoProvedor(SituacaoProvedor.EMITIDA);
        if (fatura.getIdCobrancaExterna() == null) {
            fatura.setIdCobrancaExterna(pago.idCobrancaExterna());
        }
        if (pago.forma() != null) {
            fatura.setFormaPagamento(pago.forma());
        }
        fatura.setValorPago(pago.valorPago() != null ? pago.valorPago() : fatura.getValor());
        fatura.setDataPagamento(pago.dataPagamento() != null ? pago.dataPagamento() : hoje);
        fatura.setIdTransacaoExterna(pago.idTransacao() != null ? pago.idTransacao() : pago.idCobrancaExterna());
        fatura.setUrlReciboProvedor(pago.urlReciboProvedor());
        fatura.setPagaEm(agora);
        fatura.setAtualizadoEm(agora);
    }

    static boolean pago(CobrancaProvedor cobranca) {
        return cobranca.status() == StatusCobrancaProvedor.CONFIRMADA
                || cobranca.status() == StatusCobrancaProvedor.RECEBIDA;
    }

    private void marcarCancelada(FaturaPlataforma fatura) {
        Instant agora = clock.instant();
        fatura.setStatus(StatusCobranca.CANCELADA);
        fatura.setCanceladaEm(agora);
        fatura.setAtualizadoEm(agora);
        faturaRepository.saveAndFlush(fatura);
    }

    private FaturaPlataforma daEscola(UUID escolaId, UUID faturaId) {
        return faturaRepository.findByIdAndEscolaId(faturaId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura nao encontrada"));
    }

    private Escola escola(UUID escolaId) {
        return escolaRepository.findById(escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Escola nao encontrada"));
    }

    private Escola escolaAtiva(UUID escolaId) {
        Escola escola = escola(escolaId);
        if (!escola.isAtivo()) {
            throw new IllegalArgumentException("A escola esta suspensa; nao da para lancar fatura");
        }
        return escola;
    }

    private static LocalDate competencia(String valor) {
        try {
            return YearMonth.parse(valor).atDay(1);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("competencia deve ser um mes valido no formato yyyy-MM");
        }
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }

    /**
     * O que o passo 1 leu dentro da transacao e os passos seguintes usam fora
     * dela.
     *
     * @param emitir            true quando a fatura acabou de ir para EMITINDO
     * @param idCobrancaExterna so quando ja estava EMITIDA
     * @param pagador           so quando {@code emitir}
     * @param idClienteExterno  cliente ja cadastrado na conta-pai; nulo se ainda nao
     */
    private record Reserva(UUID faturaId, UUID escolaId, BigDecimal valor, String descricao,
            LocalDate vencimentoEnviado, boolean vencida, boolean emitir, String idCobrancaExterna,
            FormaPagamento formaAtual, DadosPagador pagador, String idClienteExterno) {
    }
}
