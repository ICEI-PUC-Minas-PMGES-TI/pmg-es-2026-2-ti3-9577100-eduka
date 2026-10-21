package br.pucminas.eduka.backend.pagamento;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.StatusEventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.EventoProvedorRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.service.AvisoCobranca;
import br.pucminas.eduka.backend.service.AvisoCobrancaService;
import br.pucminas.eduka.backend.service.CobrancaParaAviso;

/**
 * Passos transacionais dos avisos do provedor, chamados so pelo
 * {@link PaymentRoutingService} (que nao abre transacao).
 *
 * <p>
 * O aviso e gravado em {@code EVENTO_PROVEDOR} antes de ser aplicado, e o id
 * externo unico garante que o mesmo aviso e aplicado uma vez so. Aplicar um
 * pagamento confirmado grava o {@link PagamentoCobranca} (origem ONLINE) e
 * marca a cobranca como PAGA na mesma transacao.
 * </p>
 *
 * <p>
 * O webhook chega sem usuario autenticado: cobranca e conta sao lidas por SQL
 * nativo, e a escola vem da propria cobranca. A conta que mandou o aviso
 * precisa ser a conta de recebimento dessa escola; senao o aviso e ignorado.
 * </p>
 */
@Component
class RoteadorEventos {

    private static final Logger log = LoggerFactory.getLogger(RoteadorEventos.class);

    private static final int MAX_MOTIVO = 500;
    private static final int MAX_REFERENCIA = 60;

    private final EventoProvedorRepository eventoRepository;
    private final CobrancaRepository cobrancaRepository;
    private final ContaRecebimentoEscolaRepository contaRepository;
    private final PagamentoCobrancaRepository pagamentoRepository;
    private final AvisoCobrancaService avisoCobrancaService;
    private final Clock clock;

    RoteadorEventos(EventoProvedorRepository eventoRepository, CobrancaRepository cobrancaRepository,
            ContaRecebimentoEscolaRepository contaRepository, PagamentoCobrancaRepository pagamentoRepository,
            AvisoCobrancaService avisoCobrancaService, Clock clock) {
        this.eventoRepository = eventoRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.contaRepository = contaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.avisoCobrancaService = avisoCobrancaService;
        this.clock = clock;
    }

    /**
     * Grava o aviso como RECEBIDO. Vazio se ele ja tinha chegado. Dois envios
     * simultaneos do mesmo aviso: o segundo falha na chave unica e o roteador
     * confere com {@link #jaRecebido}.
     */
    @Transactional
    Optional<UUID> gravar(EventoProvedor evento) {
        if (eventoRepository.existsByIdEventoExterno(evento.idEvento())) {
            return Optional.empty();
        }
        CobrancaProvedor cobranca = evento.cobranca();
        br.pucminas.eduka.backend.domain.EventoProvedor registro = br.pucminas.eduka.backend.domain.EventoProvedor
                .builder()
                .idEventoExterno(evento.idEvento())
                .tipoOriginal(evento.tipoOriginal())
                .tipo(evento.tipo())
                .referenciaExterna(cobranca == null ? null : limitar(cobranca.referenciaExterna(), MAX_REFERENCIA))
                .idContaExterna(evento.idContaExterna())
                .payload(evento.payloadSaneado())
                .build();
        return Optional.of(eventoRepository.saveAndFlush(registro).getId());
    }

    @Transactional(readOnly = true)
    boolean jaRecebido(String idEventoExterno) {
        return eventoRepository.existsByIdEventoExterno(idEventoExterno);
    }

    /** Payload do evento que ainda falta aplicar; vazio se ele ja terminou (PROCESSADO ou IGNORADO). */
    @Transactional(readOnly = true)
    Optional<String> payloadPendente(UUID eventoId) {
        return eventoRepository.findById(eventoId)
                .filter(e -> !terminado(e.getStatus()))
                .map(br.pucminas.eduka.backend.domain.EventoProvedor::getPayload);
    }

    /** Aplica o evento uma vez so: trava o registro e nao reaplica o que ja terminou. */
    @Transactional
    void aplicar(UUID eventoId, EventoProvedor evento) {
        br.pucminas.eduka.backend.domain.EventoProvedor registro = eventoRepository.travar(eventoId)
                .orElseThrow(() -> new IllegalStateException("Evento do provedor nao encontrado: " + eventoId));
        if (terminado(registro.getStatus())) {
            return;
        }
        registro.setTentativas(registro.getTentativas() + 1);

        if (evento.tipo() == TipoEventoProvedor.PAGAMENTO_RECEBIDO
                || evento.tipo() == TipoEventoProvedor.PAGAMENTO_CONFIRMADO) {
            aplicarPagamento(registro, evento);
        } else {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Tipo de aviso sem efeito no webhook: " + evento.tipo());
        }
    }

    /** Falha ao aplicar: fica em ERRO para o agendador tentar de novo. */
    @Transactional
    void marcarErro(UUID eventoId, RuntimeException erro) {
        eventoRepository.findById(eventoId).ifPresent(registro -> {
            registro.setTentativas(registro.getTentativas() + 1);
            registro.setStatus(StatusEventoProvedor.ERRO);
            registro.setMotivo(limitar(erro.getClass().getSimpleName() + ": " + erro.getMessage(), MAX_MOTIVO));
        });
    }

    private void aplicarPagamento(br.pucminas.eduka.backend.domain.EventoProvedor registro, EventoProvedor evento) {
        CobrancaProvedor pago = evento.cobranca();
        if (pago == null || pago.valorPago() == null) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Aviso de pagamento sem cobranca paga");
            return;
        }
        Optional<CobrancaLocal> encontrada = buscarCobranca(pago);
        if (encontrada.isEmpty()) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Cobranca do aviso nao encontrada no Eduka");
            return;
        }
        CobrancaLocal cobranca = encontrada.get();
        registro.setEscolaId(cobranca.aviso().escolaId());

        Optional<UUID> escolaDaConta = evento.idContaExterna() == null ? Optional.empty()
                : contaRepository.buscarEscolaPorIdContaExterna(evento.idContaExterna());
        if (!escolaDaConta.equals(Optional.of(cobranca.aviso().escolaId()))) {
            log.warn("Aviso {} de pagamento da cobranca {} veio de conta que nao e a da escola; ignorado",
                    evento.idEvento(), cobranca.aviso().id());
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Conta do aviso nao e a conta de recebimento da escola");
            return;
        }
        if (pagamentoRepository.existsByCobrancaId(cobranca.aviso().id())) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Pagamento da cobranca ja registrado");
            return;
        }

        FormaPagamento forma = pago.forma() != null ? pago.forma() : cobranca.forma();
        if (forma == null) {
            throw new IllegalStateException("Aviso de pagamento sem forma de pagamento");
        }
        LocalDate dataPagamento = pago.dataPagamento() != null ? pago.dataPagamento()
                : evento.dataEvento() != null ? evento.dataEvento() : LocalDate.now(clock);
        pagamentoRepository.save(PagamentoCobranca.builder()
                .escolaId(cobranca.aviso().escolaId())
                .cobrancaId(cobranca.aviso().id())
                .valorPago(pago.valorPago())
                .valorLiquido(pago.valorLiquido())
                .dataPagamento(dataPagamento)
                .dataCredito(pago.dataCredito())
                .formaPagamento(forma)
                .origem(OrigemPagamento.ONLINE)
                .idTransacaoExterna(pago.idTransacao() != null ? pago.idTransacao() : pago.idCobrancaExterna())
                .urlReciboProvedor(limitar(pago.urlReciboProvedor(), MAX_MOTIVO))
                .eventoId(registro.getId())
                .build());

        Instant agora = clock.instant();
        if (cobrancaRepository.marcarPagaPeloProvedor(cobranca.aviso().id(), agora) == 1) {
            avisoCobrancaService.publicar(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO, comPagaEm(cobranca.aviso(), agora));
            encerrar(registro, StatusEventoProvedor.PROCESSADO, null);
        } else {
            // Ja estava PAGA (baixa manual) ou CANCELADA: o dinheiro entrou, entao o pagamento fica registrado.
            log.warn("Pagamento online registrado para a cobranca {} que ja estava {}", cobranca.aviso().id(),
                    cobranca.status());
            encerrar(registro, StatusEventoProvedor.PROCESSADO,
                    "Pagamento registrado; a cobranca ja estava " + cobranca.status());
        }
    }

    /** Pelo id da cobranca no provedor; se ela ainda nao o tinha gravado, pela referencia {@code EDK-B-<id>}. */
    private Optional<CobrancaLocal> buscarCobranca(CobrancaProvedor pago) {
        List<Object[]> linhas = pago.idCobrancaExterna() == null ? List.of()
                : cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno(pago.idCobrancaExterna());
        if (linhas.isEmpty()) {
            Optional<UUID> id = idDaReferencia(pago.referenciaExterna());
            if (id.isPresent()) {
                linhas = cobrancaRepository.buscarParaPagamentoOnlinePorId(id.get());
            }
        }
        return linhas.stream().findFirst().map(CobrancaLocal::deLinha);
    }

    static Optional<UUID> idDaReferencia(String referencia) {
        if (referencia == null || !referencia.startsWith(RoteadorPagamento.PREFIXO_COBRANCA)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(referencia.substring(RoteadorPagamento.PREFIXO_COBRANCA.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void encerrar(br.pucminas.eduka.backend.domain.EventoProvedor registro, StatusEventoProvedor status,
            String motivo) {
        registro.setStatus(status);
        registro.setMotivo(motivo);
        registro.setProcessadoEm(clock.instant());
    }

    private static boolean terminado(StatusEventoProvedor status) {
        return status == StatusEventoProvedor.PROCESSADO || status == StatusEventoProvedor.IGNORADO;
    }

    private static CobrancaParaAviso comPagaEm(CobrancaParaAviso c, Instant pagaEm) {
        return new CobrancaParaAviso(c.id(), c.escolaId(), c.alunoPessoaId(), c.responsavelPessoaId(),
                c.descricao(), c.valor(), c.dataVencimento(), pagaEm);
    }

    private static String limitar(String valor, int max) {
        return valor == null || valor.length() <= max ? valor : valor.substring(0, max);
    }

    /** Linha de {@code CobrancaRepository#buscarParaPagamentoOnline*}: as colunas do aviso, STATUS e FORMA. */
    record CobrancaLocal(CobrancaParaAviso aviso, StatusCobranca status, FormaPagamento forma) {

        static CobrancaLocal deLinha(Object[] linha) {
            FormaPagamento forma = linha[8] == null ? null : FormaPagamento.valueOf(linha[8].toString());
            return new CobrancaLocal(CobrancaParaAviso.deLinha(linha), StatusCobranca.valueOf(linha[7].toString()),
                    forma);
        }
    }
}
