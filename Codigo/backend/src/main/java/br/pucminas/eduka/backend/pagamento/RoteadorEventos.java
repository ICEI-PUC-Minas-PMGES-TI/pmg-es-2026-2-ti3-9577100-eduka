package br.pucminas.eduka.backend.pagamento;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.StatusEventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoCadastral;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.EventoProvedorRepository;
import br.pucminas.eduka.backend.repository.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.service.AuditoriaService;
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
 * pagamento confirmado grava o {@link PagamentoCobranca} (origem ONLINE),
 * marca a cobranca como PAGA e audita, sem autor, na mesma transacao. Criacao e remocao da
 * cobranca no provedor seguem a tabela da spec §8.4. Avisos de fatura da
 * plataforma ({@code EDK-A-}) mudam a propria fatura.
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
    private final FaturaPlataformaRepository faturaRepository;
    private final AvisoCobrancaService avisoCobrancaService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    /** Avisos sobre uma cobranca; os de fatura ({@code EDK-A-}) seguem a coluna da fatura na §8.4. */
    private static final Set<TipoEventoProvedor> AVISOS_DE_COBRANCA = EnumSet.of(TipoEventoProvedor.COBRANCA_CRIADA,
            TipoEventoProvedor.PAGAMENTO_CONFIRMADO, TipoEventoProvedor.PAGAMENTO_RECEBIDO,
            TipoEventoProvedor.COBRANCA_REMOVIDA);

    RoteadorEventos(EventoProvedorRepository eventoRepository, CobrancaRepository cobrancaRepository,
            ContaRecebimentoEscolaRepository contaRepository, PagamentoCobrancaRepository pagamentoRepository,
            FaturaPlataformaRepository faturaRepository, AvisoCobrancaService avisoCobrancaService,
            AuditoriaService auditoriaService, Clock clock) {
        this.eventoRepository = eventoRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.contaRepository = contaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.faturaRepository = faturaRepository;
        this.avisoCobrancaService = avisoCobrancaService;
        this.auditoriaService = auditoriaService;
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

        Optional<UUID> faturaId = evento.cobranca() == null ? Optional.empty()
                : idDaFatura(evento.cobranca().referenciaExterna());
        if (faturaId.isPresent() && AVISOS_DE_COBRANCA.contains(evento.tipo())) {
            aplicarFatura(registro, evento, faturaId.get());
            return;
        }
        switch (evento.tipo()) {
            case PAGAMENTO_RECEBIDO, PAGAMENTO_CONFIRMADO -> aplicarPagamento(registro, evento);
            case COBRANCA_CRIADA -> aplicarCriada(registro, evento);
            case COBRANCA_REMOVIDA -> aplicarRemovida(registro, evento);
            case SITUACAO_CONTA_ALTERADA -> aplicarSituacaoConta(registro, evento);
            default -> encerrar(registro, StatusEventoProvedor.IGNORADO,
                    "Tipo de aviso sem efeito no webhook: " + evento.tipo());
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

    /**
     * Aviso de situacao da conta filha. Usa a situacao que vem no aviso, que
     * e a foto atual da conta (achado A11); sem ela, a rotina de sincronizacao
     * consulta depois. Conta que ainda nao terminou de ser criada nao muda.
     */
    private void aplicarSituacaoConta(br.pucminas.eduka.backend.domain.EventoProvedor registro,
            EventoProvedor evento) {
        if (evento.idContaExterna() == null
                || contaRepository.buscarEscolaPorIdContaExterna(evento.idContaExterna()).isEmpty()) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Aviso de conta que nao e conta de recebimento");
            return;
        }
        SituacaoContaProvedor situacao = evento.situacaoConta();
        if (situacao == null) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Aviso de conta sem situacao; a rotina sincroniza");
            return;
        }
        int gravadas = contaRepository.gravarSituacao(evento.idContaExterna(),
                RoteadorConta.statusDaSituacao(situacao).name(), nome(situacao.geral()),
                nome(situacao.documentacao()), nome(situacao.dadosComerciais()), nome(situacao.contaBancaria()),
                clock.instant());
        if (gravadas == 0) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Conta de recebimento ainda em criacao");
            return;
        }
        encerrar(registro, StatusEventoProvedor.PROCESSADO, null);
    }

    private static String nome(SituacaoCadastral situacao) {
        return situacao == null ? null : situacao.name();
    }

    private void aplicarPagamento(br.pucminas.eduka.backend.domain.EventoProvedor registro, EventoProvedor evento) {
        CobrancaProvedor pago = evento.cobranca();
        if (pago == null || pago.valorPago() == null) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Aviso de pagamento sem cobranca paga");
            return;
        }
        Optional<CobrancaLocal> encontrada = cobrancaDaEscola(registro, evento);
        if (encontrada.isEmpty()) {
            return;
        }
        CobrancaLocal cobranca = encontrada.get();
        Instant agora = clock.instant();
        if (cobranca.situacao() == SituacaoProvedor.EMITINDO && pago.idCobrancaExterna() != null) {
            // Pagou antes de a resposta da criacao chegar: a cobranca tambem passa a EMITIDA.
            cobrancaRepository.marcarEmitidaPeloProvedor(cobranca.aviso().id(), pago.idCobrancaExterna(), agora);
        }
        Optional<PagamentoCobranca> registrado = pagamentoRepository.findByCobrancaId(cobranca.aviso().id());
        if (registrado.isPresent()) {
            completarCredito(registro, registrado.get(), pago);
            return;
        }

        LocalDate dataPadrao = evento.dataEvento() != null ? evento.dataEvento() : LocalDate.now(clock);
        switch (gravarPagamento(cobranca, pago, dataPadrao, registro.getId(), agora)) {
            case PAGA -> encerrar(registro, StatusEventoProvedor.PROCESSADO, null);
            case PAGA_APOS_CANCELAMENTO -> encerrar(registro, StatusEventoProvedor.PROCESSADO,
                    "Pagamento apos cancelamento; cobranca marcada como PAGA");
            case JA_ESTAVA_PAGA -> encerrar(registro, StatusEventoProvedor.PROCESSADO,
                    "Pagamento registrado; a cobranca ja estava " + cobranca.status());
        }
    }

    /**
     * Pagamento que o provedor ja tinha recebido, achado fora do webhook (ao
     * cancelar, spec §8.5). Grava como o aviso de pagamento, sem evento; se o
     * aviso ja o gravou, nada muda. Sem o valor pago, fica para o aviso.
     */
    @Transactional
    void registrarPagamentoEncontrado(UUID cobrancaId, CobrancaProvedor pago) {
        Optional<CobrancaLocal> encontrada = cobrancaRepository.buscarParaPagamentoOnlinePorId(cobrancaId).stream()
                .findFirst()
                .map(CobrancaLocal::deLinha);
        if (encontrada.isEmpty() || pago.valorPago() == null
                || pagamentoRepository.findByCobrancaId(cobrancaId).isPresent()) {
            return;
        }
        gravarPagamento(encontrada.get(), pago, LocalDate.now(clock), null, clock.instant());
    }

    private enum PagamentoGravado {
        PAGA,
        PAGA_APOS_CANCELAMENTO,
        JA_ESTAVA_PAGA
    }

    /** Grava o {@link PagamentoCobranca} (origem ONLINE), marca a cobranca como PAGA e audita. */
    private PagamentoGravado gravarPagamento(CobrancaLocal cobranca, CobrancaProvedor pago, LocalDate dataPadrao,
            UUID eventoId, Instant agora) {
        FormaPagamento forma = pago.forma() != null ? pago.forma() : cobranca.forma();
        if (forma == null) {
            throw new IllegalStateException("Aviso de pagamento sem forma de pagamento");
        }
        pagamentoRepository.save(PagamentoCobranca.builder()
                .escolaId(cobranca.aviso().escolaId())
                .cobrancaId(cobranca.aviso().id())
                .valorPago(pago.valorPago())
                .valorLiquido(pago.valorLiquido())
                .dataPagamento(pago.dataPagamento() != null ? pago.dataPagamento() : dataPadrao)
                .dataCredito(pago.dataCredito())
                .formaPagamento(forma)
                .origem(OrigemPagamento.ONLINE)
                .idTransacaoExterna(pago.idTransacao() != null ? pago.idTransacao() : pago.idCobrancaExterna())
                .urlReciboProvedor(limitar(pago.urlReciboProvedor(), MAX_MOTIVO))
                .eventoId(eventoId)
                .build());

        if (cobrancaRepository.marcarPagaPeloProvedor(cobranca.aviso().id(), agora) == 1) {
            auditoriaService.registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO, EntidadeAuditada.COBRANCA,
                    cobranca.aviso().id(), cobranca.aviso().escolaId());
            avisoCobrancaService.publicar(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO, comPagaEm(cobranca.aviso(), agora));
            return PagamentoGravado.PAGA;
        }
        if (cobrancaRepository.marcarPagaAposCancelamento(cobranca.aviso().id(), agora) == 1) {
            // R23: o que o provedor diz sobre o dinheiro prevalece. Alerta so no log ate existir a auditoria (RF22.1).
            log.warn("PAGAMENTO_APOS_CANCELAMENTO: cobranca {} cancelada foi paga no provedor; marcada como PAGA",
                    cobranca.aviso().id());
            return PagamentoGravado.PAGA_APOS_CANCELAMENTO;
        }
        // Ja estava PAGA (baixa manual): o dinheiro entrou, entao o pagamento fica registrado.
        log.warn("Pagamento online registrado para a cobranca {} que ja estava {}", cobranca.aviso().id(),
                cobranca.status());
        return PagamentoGravado.JA_ESTAVA_PAGA;
    }

    /** CONFIRMED seguido de RECEIVED: um pagamento so; o aviso seguinte so completa o credito (R23). */
    private void completarCredito(br.pucminas.eduka.backend.domain.EventoProvedor registro,
            PagamentoCobranca pagamento, CobrancaProvedor pago) {
        boolean completou = false;
        if (pagamento.getDataCredito() == null && pago.dataCredito() != null) {
            pagamento.setDataCredito(pago.dataCredito());
            completou = true;
        }
        if (pagamento.getValorLiquido() == null && pago.valorLiquido() != null) {
            pagamento.setValorLiquido(pago.valorLiquido());
            completou = true;
        }
        if (completou) {
            encerrar(registro, StatusEventoProvedor.PROCESSADO, "Pagamento ja registrado; credito completado");
        } else {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Pagamento da cobranca ja registrado");
        }
    }

    /** O provedor confirmou a criacao: a cobranca que ficou EMITINDO (resposta perdida) passa a EMITIDA. */
    private void aplicarCriada(br.pucminas.eduka.backend.domain.EventoProvedor registro, EventoProvedor evento) {
        Optional<CobrancaLocal> encontrada = cobrancaDaEscola(registro, evento);
        if (encontrada.isEmpty()) {
            return;
        }
        CobrancaLocal cobranca = encontrada.get();
        String idExterno = evento.cobranca().idCobrancaExterna();
        if (cobranca.situacao() == SituacaoProvedor.EMITINDO && idExterno != null
                && cobrancaRepository.marcarEmitidaPeloProvedor(cobranca.aviso().id(), idExterno,
                        clock.instant()) == 1) {
            encerrar(registro, StatusEventoProvedor.PROCESSADO, null);
        } else {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Sem efeito: a cobranca ja estava " + cobranca.situacao());
        }
    }

    /**
     * Cobranca removida no provedor: a que ainda estava EMITINDO vira
     * FALHA_EMISSAO (pode ser emitida de novo); a emitida e em aberto vira
     * CANCELADA. Paga ou ja cancelada: sem efeito.
     */
    private void aplicarRemovida(br.pucminas.eduka.backend.domain.EventoProvedor registro, EventoProvedor evento) {
        Optional<CobrancaLocal> encontrada = cobrancaDaEscola(registro, evento);
        if (encontrada.isEmpty()) {
            return;
        }
        CobrancaLocal cobranca = encontrada.get();
        UUID id = cobranca.aviso().id();
        Instant agora = clock.instant();
        if (cobranca.situacao() == SituacaoProvedor.EMITINDO
                && cobrancaRepository.marcarFalhaEmissaoPeloProvedor(id, "Cobranca removida no provedor", agora) == 1) {
            encerrar(registro, StatusEventoProvedor.PROCESSADO, null);
        } else if (cobrancaRepository.cancelarPeloProvedor(id, agora) == 1) {
            // Auditoria "removida no provedor" fica para a RF22.1.
            log.warn("Cobranca {} removida no provedor; marcada como CANCELADA", id);
            encerrar(registro, StatusEventoProvedor.PROCESSADO, "Cobranca removida no provedor");
        } else {
            if (cobranca.status() == StatusCobranca.PAGA) {
                log.warn("Cobranca {} ja paga foi removida no provedor; nada muda no Eduka", id);
            }
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Sem efeito: a cobranca ja estava " + cobranca.status());
        }
    }

    /**
     * Aviso de fatura da plataforma (Fluxo A), pela coluna da fatura na §8.4.
     * A fatura e cobrada na conta-pai: aviso que vem da conta de uma escola e
     * ignorado, porque a escola poderia criar na propria conta uma cobranca
     * com a referencia da fatura e paga-la a si mesma.
     */
    private void aplicarFatura(br.pucminas.eduka.backend.domain.EventoProvedor registro, EventoProvedor evento,
            UUID faturaId) {
        if (evento.idContaExterna() != null
                && contaRepository.buscarEscolaPorIdContaExterna(evento.idContaExterna()).isPresent()) {
            log.warn("Aviso {} da fatura {} veio da conta de uma escola, nao da conta-pai; ignorado",
                    evento.idEvento(), faturaId);
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Aviso de fatura fora da conta-pai");
            return;
        }
        Optional<FaturaPlataforma> encontrada = faturaRepository.travar(faturaId);
        if (encontrada.isEmpty()) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Fatura do aviso nao encontrada no Eduka");
            return;
        }
        FaturaPlataforma fatura = encontrada.get();
        registro.setEscolaId(fatura.getEscolaId());
        CobrancaProvedor doAviso = evento.cobranca();
        Instant agora = clock.instant();

        switch (evento.tipo()) {
            case COBRANCA_CRIADA -> {
                if (fatura.getSituacaoProvedor() == SituacaoProvedor.EMITINDO && doAviso.idCobrancaExterna() != null) {
                    fatura.setSituacaoProvedor(SituacaoProvedor.EMITIDA);
                    fatura.setIdCobrancaExterna(doAviso.idCobrancaExterna());
                    fatura.setEmitidaEm(agora);
                    fatura.setUltimoErroProvedor(null);
                    fatura.setAtualizadoEm(agora);
                    encerrar(registro, StatusEventoProvedor.PROCESSADO, null);
                } else {
                    encerrar(registro, StatusEventoProvedor.IGNORADO,
                            "Sem efeito: a fatura ja estava " + fatura.getSituacaoProvedor());
                }
            }
            case PAGAMENTO_CONFIRMADO, PAGAMENTO_RECEBIDO -> {
                if (doAviso.valorPago() == null) {
                    encerrar(registro, StatusEventoProvedor.IGNORADO, "Aviso de pagamento sem cobranca paga");
                } else if (fatura.getStatus() == StatusCobranca.PAGA) {
                    encerrar(registro, StatusEventoProvedor.IGNORADO, "Pagamento da fatura ja registrado");
                } else {
                    StatusCobranca antes = fatura.getStatus();
                    RoteadorFatura.registrarPagamento(fatura, doAviso,
                            evento.dataEvento() != null ? evento.dataEvento() : LocalDate.now(clock), agora);
                    if (antes == StatusCobranca.CANCELADA) {
                        // R23: o dinheiro prevalece. Alerta so no log ate existir a auditoria (RF22.1).
                        log.warn("PAGAMENTO_APOS_CANCELAMENTO: fatura {} cancelada foi paga; marcada como PAGA",
                                faturaId);
                        encerrar(registro, StatusEventoProvedor.PROCESSADO,
                                "Pagamento apos cancelamento; fatura marcada como PAGA");
                    } else {
                        encerrar(registro, StatusEventoProvedor.PROCESSADO, null);
                    }
                }
            }
            case COBRANCA_REMOVIDA -> {
                if (fatura.getSituacaoProvedor() == SituacaoProvedor.EMITINDO) {
                    fatura.setSituacaoProvedor(SituacaoProvedor.FALHA_EMISSAO);
                    fatura.setUltimoErroProvedor("Cobranca removida no provedor");
                    fatura.setAtualizadoEm(agora);
                    encerrar(registro, StatusEventoProvedor.PROCESSADO, null);
                } else if (fatura.getSituacaoProvedor() == SituacaoProvedor.EMITIDA && fatura.getStatus().emAberto()) {
                    fatura.setStatus(StatusCobranca.CANCELADA);
                    fatura.setCanceladaEm(agora);
                    fatura.setAtualizadoEm(agora);
                    encerrar(registro, StatusEventoProvedor.PROCESSADO, "Fatura removida no provedor");
                } else {
                    encerrar(registro, StatusEventoProvedor.IGNORADO,
                            "Sem efeito: a fatura ja estava " + fatura.getStatus());
                }
            }
            default -> encerrar(registro, StatusEventoProvedor.IGNORADO,
                    "Tipo de aviso sem efeito no webhook: " + evento.tipo());
        }
    }

    static Optional<UUID> idDaFatura(String referencia) {
        if (referencia == null || !referencia.startsWith(RoteadorFatura.PREFIXO_FATURA)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(referencia.substring(RoteadorFatura.PREFIXO_FATURA.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * A cobranca do aviso, se a conta que avisou for a conta de recebimento da
     * escola dela (R21). Vazio quando o evento ja foi encerrado como IGNORADO.
     */
    private Optional<CobrancaLocal> cobrancaDaEscola(br.pucminas.eduka.backend.domain.EventoProvedor registro,
            EventoProvedor evento) {
        if (evento.cobranca() == null) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Aviso de cobranca sem a cobranca");
            return Optional.empty();
        }
        Optional<CobrancaLocal> encontrada = buscarCobranca(evento.cobranca());
        if (encontrada.isEmpty()) {
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Cobranca do aviso nao encontrada no Eduka");
            return Optional.empty();
        }
        CobrancaLocal cobranca = encontrada.get();
        registro.setEscolaId(cobranca.aviso().escolaId());

        Optional<UUID> escolaDaConta = evento.idContaExterna() == null ? Optional.empty()
                : contaRepository.buscarEscolaPorIdContaExterna(evento.idContaExterna());
        if (!escolaDaConta.equals(Optional.of(cobranca.aviso().escolaId()))) {
            log.warn("Aviso {} da cobranca {} veio de conta que nao e a da escola; ignorado", evento.idEvento(),
                    cobranca.aviso().id());
            encerrar(registro, StatusEventoProvedor.IGNORADO, "Conta do aviso nao e a conta de recebimento da escola");
            return Optional.empty();
        }
        return encontrada;
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

    /**
     * Grava o fim do evento com {@code save} explicito: os UPDATE nativos da
     * cobranca ({@code clearAutomatically}) desanexam o registro, e a mudanca
     * so no objeto se perderia.
     */
    private void encerrar(br.pucminas.eduka.backend.domain.EventoProvedor registro, StatusEventoProvedor status,
            String motivo) {
        registro.setStatus(status);
        registro.setMotivo(motivo);
        registro.setProcessadoEm(clock.instant());
        eventoRepository.save(registro);
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

    /**
     * Linha de {@code CobrancaRepository#buscarParaPagamentoOnline*}: as
     * colunas do aviso, STATUS, FORMA e SITUACAO_PROVEDOR.
     */
    record CobrancaLocal(CobrancaParaAviso aviso, StatusCobranca status, FormaPagamento forma,
            SituacaoProvedor situacao) {

        static CobrancaLocal deLinha(Object[] linha) {
            FormaPagamento forma = linha[8] == null ? null : FormaPagamento.valueOf(linha[8].toString());
            return new CobrancaLocal(CobrancaParaAviso.deLinha(linha), StatusCobranca.valueOf(linha[7].toString()),
                    forma, SituacaoProvedor.valueOf(linha[9].toString()));
        }
    }
}
