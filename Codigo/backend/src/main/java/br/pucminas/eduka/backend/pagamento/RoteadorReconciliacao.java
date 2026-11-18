package br.pucminas.eduka.backend.pagamento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.domain.escola.FaturaPlataforma;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.repository.escola.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository.EmissaoSemResposta;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.security.PiiCryptoService;

/**
 * Reconciliacao das emissoes sem resposta (spec §8.4, R15), por tras do
 * {@link PaymentRoutingService}.
 *
 * <p>
 * Criacao que estourou o tempo deixa a cobranca ou a fatura EMITINDO sem
 * saber se ela existe no provedor. Se o aviso de criacao tambem nao chegou, a
 * rotina procura pela referencia ({@code EDK-B-} na conta da escola,
 * {@code EDK-A-} na conta-pai) e grava o mesmo resultado do aviso. Chama o
 * provedor sem transacao; cada gravacao e uma transacao curta no
 * {@link RoteadorEventos}.
 * </p>
 *
 * <p>
 * Tambem confere no provedor as faturas EMITIDAS em aberto: a conta-pai pode
 * nao ter webhook, e sem o aviso a fatura paga ficaria em aberto no Eduka.
 * </p>
 */
@Component
class RoteadorReconciliacao {

    private static final Logger log = LoggerFactory.getLogger(RoteadorReconciliacao.class);

    /** Bem acima do tempo de resposta do provedor: a emissao mais nova ainda pode estar em andamento. */
    static final Duration EMISSAO_SEM_RESPOSTA = Duration.ofMinutes(5);

    private final CobrancaRepository cobrancaRepository;
    private final FaturaPlataformaRepository faturaRepository;
    private final RoteadorEventos eventos;
    private final PiiCryptoService piiCryptoService;
    private final ChamadorProvedor provedor;
    private final Clock clock;

    RoteadorReconciliacao(CobrancaRepository cobrancaRepository, FaturaPlataformaRepository faturaRepository,
            RoteadorEventos eventos, PiiCryptoService piiCryptoService, ChamadorProvedor provedor, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.faturaRepository = faturaRepository;
        this.eventos = eventos;
        this.piiCryptoService = piiCryptoService;
        this.provedor = provedor;
        this.clock = clock;
    }

    /**
     * @param lote quantas cobrancas e quantas faturas por execucao, as mais antigas primeiro
     * @return quantas sairam de EMITINDO mais quantas faturas EMITIDAS foram pagas
     */
    int reconciliar(int lote) {
        Instant iniciadaAntes = clock.instant().minus(EMISSAO_SEM_RESPOSTA);
        return reconciliarCobrancas(iniciadaAntes, lote) + reconciliarFaturas(iniciadaAntes, lote)
                + conferirFaturasEmitidas(lote);
    }

    private int reconciliarCobrancas(Instant iniciadaAntes, int lote) {
        int resolvidas = 0;
        for (EmissaoSemResposta emissao : cobrancaRepository.buscarEmissoesSemResposta(iniciadaAntes, lote)) {
            try {
                // R8: a credencial so e decifrada aqui, na hora da chamada.
                CredencialProvedor credencial = new CredencialProvedor(
                        piiCryptoService.decifrar(emissao.getCredencialCifrada()));
                String referencia = RoteadorPagamento.PREFIXO_COBRANCA + emissao.getId();
                Optional<CobrancaProvedor> noProvedor = provedor
                        .chamar(p -> p.buscarCobrancaPorReferencia(credencial, referencia));
                if (eventos.reconciliarCobranca(emissao.getId(), noProvedor)) {
                    resolvidas++;
                }
            } catch (RuntimeException e) {
                // Continua EMITINDO; a proxima execucao tenta de novo.
                log.warn("Nao foi possivel reconciliar a cobranca {}: {}", emissao.getId(),
                        e.getClass().getSimpleName());
            }
        }
        return resolvidas;
    }

    private int reconciliarFaturas(Instant iniciadaAntes, int lote) {
        List<UUID> faturas = faturaRepository.buscarEmissoesSemResposta(iniciadaAntes, PageRequest.of(0, lote));
        if (faturas.isEmpty()) {
            return 0;
        }
        CredencialProvedor credencial;
        try {
            credencial = provedor.chamar(PaymentProvider::credencialDaContaPai);
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel reconciliar as faturas: {}", e.getClass().getSimpleName());
            return 0;
        }
        int resolvidas = 0;
        for (UUID faturaId : faturas) {
            try {
                String referencia = RoteadorFatura.PREFIXO_FATURA + faturaId;
                Optional<CobrancaProvedor> noProvedor = provedor
                        .chamar(p -> p.buscarCobrancaPorReferencia(credencial, referencia));
                if (eventos.reconciliarFatura(faturaId, noProvedor)) {
                    resolvidas++;
                }
            } catch (RuntimeException e) {
                log.warn("Nao foi possivel reconciliar a fatura {}: {}", faturaId, e.getClass().getSimpleName());
            }
        }
        return resolvidas;
    }

    private int conferirFaturasEmitidas(int lote) {
        List<FaturaPlataforma> faturas = faturaRepository.buscarEmitidasEmAberto(PageRequest.of(0, lote));
        if (faturas.isEmpty()) {
            return 0;
        }
        CredencialProvedor credencial;
        try {
            credencial = provedor.chamar(PaymentProvider::credencialDaContaPai);
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel conferir as faturas emitidas: {}", e.getClass().getSimpleName());
            return 0;
        }
        int pagas = 0;
        for (FaturaPlataforma fatura : faturas) {
            try {
                CobrancaProvedor noProvedor = provedor
                        .chamar(p -> p.consultarCobranca(credencial, fatura.getIdCobrancaExterna()));
                if (RoteadorFatura.pago(noProvedor)
                        && eventos.registrarPagamentoDaFaturaEncontrado(fatura.getId(), noProvedor)) {
                    pagas++;
                }
            } catch (RuntimeException e) {
                // Continua em aberto; a proxima execucao confere de novo.
                log.warn("Nao foi possivel conferir a fatura {}: {}", fatura.getId(), e.getClass().getSimpleName());
            }
        }
        return pagas;
    }
}
