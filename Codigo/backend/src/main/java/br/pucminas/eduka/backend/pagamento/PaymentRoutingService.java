package br.pucminas.eduka.backend.pagamento;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.dto.AtivarRecebimentosRequest;
import br.pucminas.eduka.backend.dto.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.ContaRecebimentoResponse;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.HabilitacaoRecebimentos;
import br.pucminas.eduka.backend.dto.LancarFaturaRequest;
import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/**
 * Roteador de pagamentos: unica porta de entrada dos casos de uso que envolvem
 * o provedor. Contem as regras de negocio e as checagens de seguranca (posse,
 * perfil, estado, habilitacao, idempotencia), escolhe a conta e a credencial,
 * controla as transacoes e audita.
 *
 * <p>
 * So o roteador chama o {@link PaymentProvider}, sempre pelo
 * {@link ChamadorProvedor} e nunca com transacao aberta. So o roteador decifra
 * credenciais. Esta classe nao e {@code @Transactional}: cada passo que grava
 * fica num metodo transacional de outro bean.
 * </p>
 *
 * <p>
 * Esqueleto da PO.1: cada tarefa da sprint implementa os seus metodos, de
 * preferencia nas classes package-private do pacote ({@code RoteadorConta},
 * {@code RoteadorPagamento}, {@code RoteadorEventos}, {@code RoteadorFatura}).
 * </p>
 */
@Service
public class PaymentRoutingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRoutingService.class);

    private final ChamadorProvedor provedor;
    private final RoteadorConta roteadorConta;
    private final RoteadorPagamento roteadorPagamento;
    private final RoteadorEventos eventos;

    PaymentRoutingService(ChamadorProvedor provedor, RoteadorConta roteadorConta,
            RoteadorPagamento roteadorPagamento, RoteadorEventos eventos) {
        this.provedor = provedor;
        this.roteadorConta = roteadorConta;
        this.roteadorPagamento = roteadorPagamento;
        this.eventos = eventos;
    }

    // ---- Conta de recebimento (MASTER da escola do token) ----

    public ContaRecebimentoResponse consultarConta() {
        throw naoImplementado("NOVA-3");
    }

    /** Spec §8.1. */
    public ContaRecebimentoResponse ativarRecebimentos(AtivarRecebimentosRequest dados) {
        throw naoImplementado("NOVA-3");
    }

    /** R10. */
    public ContaRecebimentoResponse sincronizarConta() {
        throw naoImplementado("NOVA-3");
    }

    /** R11. Nunca lanca excecao: devolve o motivo quando nao habilitado. */
    public HabilitacaoRecebimentos habilitacao(UUID escolaId) {
        return roteadorConta.habilitacao(escolaId);
    }

    // ---- Pagar ----

    /** ALUNO dono da cobranca; spec §8.2. */
    public DadosPagamentoResponse pagarCobranca(UUID cobrancaId, FormaPagamento forma) {
        return roteadorPagamento.pagarCobranca(cobrancaId, forma);
    }

    /** MASTER da escola; spec §8.2. */
    public DadosPagamentoResponse pagarFatura(UUID faturaId, FormaPagamento forma) {
        throw naoImplementado("NOVA-5");
    }

    /** ALUNO dono, MASTER/FINANCEIRO da escola. */
    public ComprovanteResponse comprovanteCobranca(UUID cobrancaId) {
        throw naoImplementado("RF16.1");
    }

    /** MASTER da escola, SUPER_ADMIN. */
    public ComprovanteResponse comprovanteFatura(UUID faturaId) {
        throw naoImplementado("NOVA-5");
    }

    // ---- Chamados por CobrancaService ----

    /** R24. */
    public boolean permiteEditar(Cobranca cobranca) {
        throw naoImplementado("RF12.1");
    }

    /** Spec §8.5. */
    public void cancelarNoProvedor(Cobranca cobranca) {
        throw naoImplementado("RF12.1");
    }

    // ---- Fluxo A (SUPER_ADMIN) ----

    public FaturaPlataformaResponse lancarFatura(UUID escolaId, LancarFaturaRequest dados) {
        throw naoImplementado("NOVA-5");
    }

    public void cancelarFatura(UUID faturaId) {
        throw naoImplementado("NOVA-5");
    }

    // ---- Eventos e consistencia ----

    /**
     * Webhook; spec §8.3. Confere o token, grava o aviso e o aplica. Aviso
     * repetido e descartado sem erro. Uma falha ao aplicar nao falha o
     * webhook: o aviso ja esta gravado e fica em ERRO para o agendador.
     *
     * @throws WebhookNaoAutorizadoException token ausente ou diferente
     * @throws IllegalArgumentException      corpo ilegivel ou sem id do aviso
     */
    public void registrarEvento(String tokenRecebido, String corpoJson) {
        if (!provedor.chamar(p -> p.webhookAutentico(tokenRecebido))) {
            throw new WebhookNaoAutorizadoException();
        }
        EventoProvedor evento = provedor.chamar(p -> p.interpretarEvento(corpoJson));
        if (evento.idEvento() == null || evento.idEvento().isBlank()) {
            throw new IllegalArgumentException("Aviso do provedor sem id");
        }

        Optional<UUID> eventoId;
        try {
            eventoId = eventos.gravar(evento);
        } catch (DataIntegrityViolationException e) {
            // O mesmo aviso chegando em paralelo esbarra na chave unica.
            if (!eventos.jaRecebido(evento.idEvento())) {
                throw e;
            }
            eventoId = Optional.empty();
        }
        eventoId.ifPresentOrElse(id -> aplicar(id, evento),
                () -> log.info("Aviso {} do provedor repetido; descartado", evento.idEvento()));
    }

    /**
     * Aplica o evento uma vez. A escola sai da cobranca do evento, nao do
     * usuario (o webhook nao tem usuario). Nunca lanca excecao: a falha deixa o
     * evento em ERRO.
     */
    public void processarEvento(UUID eventoId) {
        try {
            Optional<String> payload = eventos.payloadPendente(eventoId);
            if (payload.isEmpty()) {
                return;
            }
            EventoProvedor evento = provedor.chamar(p -> p.interpretarEvento(payload.get()));
            aplicar(eventoId, evento);
        } catch (RuntimeException e) {
            falhou(eventoId, e);
        }
    }

    private void aplicar(UUID eventoId, EventoProvedor evento) {
        try {
            eventos.aplicar(eventoId, evento);
        } catch (RuntimeException e) {
            falhou(eventoId, e);
        }
    }

    private void falhou(UUID eventoId, RuntimeException erro) {
        log.error("Falha ao aplicar o evento {} do provedor", eventoId, erro);
        try {
            eventos.marcarErro(eventoId, erro);
        } catch (RuntimeException e) {
            // Continua RECEBIDO ou PROCESSANDO; o agendador pega do mesmo jeito.
            log.error("Nao foi possivel marcar o evento {} com ERRO", eventoId, e);
        }
    }

    /** Agendador. */
    public void processarEventosPendentes() {
        throw naoImplementado("NOVA-4");
    }

    /** Agendador; spec §8.4. */
    public void reconciliarEmissoesPendentes() {
        throw naoImplementado("NOVA-4");
    }

    /** Agendador. */
    public void sincronizarContasAguardando() {
        throw naoImplementado("NOVA-4");
    }

    private static UnsupportedOperationException naoImplementado(String tarefa) {
        return new UnsupportedOperationException("Ainda nao implementado (tarefa " + tarefa + ")");
    }
}
