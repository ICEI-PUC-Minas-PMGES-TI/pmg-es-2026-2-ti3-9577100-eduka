package br.pucminas.eduka.backend.pagamento;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.LancarFaturaRequest;
import br.pucminas.eduka.backend.exception.CobrancaJaPagaException;
import br.pucminas.eduka.backend.exception.FaturaDuplicadaException;
import br.pucminas.eduka.backend.exception.PagamentoEmProcessamentoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Fatura da plataforma (Fluxo A, R26), por tras do {@link PaymentRoutingService}:
 * o Super Admin lanca, lista e cancela; o MASTER da escola lista.
 *
 * <p>
 * A cobranca da fatura fica na conta-pai do Eduka. Cancelar fatura ja emitida
 * remove primeiro no provedor, fora de transacao (R25).
 * </p>
 */
@Component
class RoteadorFatura {

    /** O vencimento segue o calendario da escola, nao o de UTC. */
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final FaturaPlataformaRepository faturaRepository;
    private final EscolaRepository escolaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final ChamadorProvedor provedor;
    private final TransactionTemplate transacao;
    private final Clock clock;

    RoteadorFatura(FaturaPlataformaRepository faturaRepository, EscolaRepository escolaRepository,
            IsolamentoEscola isolamentoEscola, ChamadorProvedor provedor,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.faturaRepository = faturaRepository;
        this.escolaRepository = escolaRepository;
        this.isolamentoEscola = isolamentoEscola;
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
                transacao.executeWithoutResult(status -> registrarPagamento(daEscola(escolaId, faturaId),
                        noProvedor, hoje(), clock.instant()));
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
}
