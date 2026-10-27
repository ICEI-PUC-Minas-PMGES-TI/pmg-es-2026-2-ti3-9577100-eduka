package br.pucminas.eduka.backend.pagamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
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
    private final RoteadorFatura roteadorFatura;
    private final RoteadorSandbox roteadorSandbox;

    PaymentRoutingService(ChamadorProvedor provedor, RoteadorConta roteadorConta,
            RoteadorPagamento roteadorPagamento, RoteadorEventos eventos, RoteadorFatura roteadorFatura,
            RoteadorSandbox roteadorSandbox) {
        this.provedor = provedor;
        this.roteadorConta = roteadorConta;
        this.roteadorPagamento = roteadorPagamento;
        this.eventos = eventos;
        this.roteadorFatura = roteadorFatura;
        this.roteadorSandbox = roteadorSandbox;
    }

    // ---- Conta de recebimento (MASTER da escola do token) ----

    public ContaRecebimentoResponse consultarConta() {
        return roteadorConta.consultar();
    }

    /** Spec §8.1. */
    public ContaRecebimentoResponse ativarRecebimentos(AtivarRecebimentosRequest dados) {
        return roteadorConta.ativar(dados);
    }

    /** R10. */
    public ContaRecebimentoResponse sincronizarConta() {
        return roteadorConta.sincronizarDaEscola();
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

    /** ALUNO dono da cobranca: PDF do boleto ja emitido, sem expor o link do provedor. */
    public byte[] baixarBoleto(UUID cobrancaId) {
        return roteadorPagamento.baixarBoleto(cobrancaId);
    }

    /** MASTER da escola; spec §8.2. */
    public DadosPagamentoResponse pagarFatura(UUID faturaId, FormaPagamento forma) {
        return roteadorFatura.pagar(faturaId, forma);
    }

    /** ALUNO dono, MASTER/FINANCEIRO da escola; R28 a R30. */
    public ComprovanteResponse comprovanteCobranca(UUID cobrancaId) {
        return roteadorPagamento.comprovante(cobrancaId);
    }

    /** SUPER_ADMIN, pela escola do caminho (R29). */
    public ComprovanteResponse comprovanteFatura(UUID escolaId, UUID faturaId) {
        return roteadorFatura.comprovante(escolaId, faturaId);
    }

    /** MASTER, so da escola do token (R29). */
    public ComprovanteResponse comprovanteMinhaFatura(UUID faturaId) {
        return roteadorFatura.comprovanteMinha(faturaId);
    }

    // ---- Chamados por CobrancaService ----

    /**
     * R24: depois que alguem clicou em Pagar, a cobranca existe (ou pode
     * existir) no provedor e nao e mais editada. Emissao recusada nao criou
     * nada no provedor: a escola pode corrigir a cobranca.
     */
    public boolean permiteEditar(Cobranca cobranca) {
        return cobranca.getSituacaoProvedor() == SituacaoProvedor.NAO_EMITIDA
                || cobranca.getSituacaoProvedor() == SituacaoProvedor.FALHA_EMISSAO;
    }

    /**
     * Spec §8.5: remove no provedor a cobranca ja emitida, antes de cancelar
     * no Eduka. Chamar sem transacao aberta. Recusa com
     * {@code CobrancaJaPagaException} se o provedor ja recebeu, e com
     * {@code PagamentoEmProcessamentoException} se a cobranca esta sendo gerada.
     */
    public void cancelarNoProvedor(Cobranca cobranca) {
        roteadorPagamento.cancelarNoProvedor(cobranca);
    }

    // ---- Fluxo A (SUPER_ADMIN) ----

    /** R26. */
    public FaturaPlataformaResponse lancarFatura(UUID escolaId, LancarFaturaRequest dados) {
        return roteadorFatura.lancar(escolaId, dados);
    }

    public List<FaturaPlataformaResponse> listarFaturas(UUID escolaId) {
        return roteadorFatura.listarDaEscola(escolaId);
    }

    /** R26, com a regra do R25 para fatura ja emitida. */
    public FaturaPlataformaResponse cancelarFatura(UUID escolaId, UUID faturaId) {
        return roteadorFatura.cancelar(escolaId, faturaId);
    }

    /** Rotina diaria de atraso (spec §7.4), sem usuario autenticado. */
    public int marcarFaturasVencidas() {
        return roteadorFatura.marcarVencidasComoEmAtraso();
    }

    // ---- Fluxo A (MASTER da escola do token) ----

    public List<FaturaPlataformaResponse> listarMinhasFaturas() {
        return roteadorFatura.listarMinhas();
    }

    // ---- Eventos e consistencia ----

    /**
     * Webhook; spec §8.3. Confere o token, grava o aviso e o aplica. Aviso
     * repetido e descartado sem erro. Uma falha ao aplicar nao falha o
     * webhook: o aviso ja esta gravado e fica em ERRO para o agendador.
     *
     * @throws WebhookNaoAutorizadoException token ausente ou diferente
     * @throws IllegalArgumentException      corpo ilegivel ou sem id ou tipo do aviso
     */
    public void registrarEvento(String tokenRecebido, String corpoJson) {
        if (!provedor.chamar(p -> p.webhookAutentico(tokenRecebido))) {
            throw new WebhookNaoAutorizadoException();
        }
        EventoProvedor evento = provedor.chamar(p -> p.interpretarEvento(corpoJson));
        if (evento.idEvento() == null || evento.idEvento().isBlank()) {
            throw new IllegalArgumentException("Aviso do provedor sem id");
        }
        if (evento.tipoOriginal() == null || evento.tipoOriginal().isBlank()) {
            throw new IllegalArgumentException("Aviso do provedor sem tipo");
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

    /**
     * Agendador, sem usuario autenticado: reaplica os avisos que nao terminaram
     * (falha ao aplicar ou aplicacao interrompida), com o mesmo resultado do
     * webhook. Cada aviso e aplicado uma vez so (trava do registro) e uma falha
     * nao para os outros.
     *
     * @param maxTentativas aviso com essa quantidade de tentativas nao volta
     * @param lote          quantos avisos por execucao, os mais antigos primeiro
     * @return quantos avisos foram reaplicados
     */
    public int processarEventosPendentes(int maxTentativas, int lote) {
        List<UUID> pendentes = eventos.pendentes(maxTentativas, lote);
        pendentes.forEach(this::processarEvento);
        return pendentes.size();
    }

    /** Agendador; spec §8.4. */
    public void reconciliarEmissoesPendentes() {
        throw naoImplementado("NOVA-4");
    }

    /**
     * Agendador, sem usuario autenticado.
     *
     * @return quantas contas tiveram a situacao lida do provedor
     */
    public int sincronizarContasAguardando() {
        return roteadorConta.sincronizarAguardando();
    }

    // ---- Atalhos de Sandbox (NOVA-6, so no perfil dev) ----

    /** MASTER da escola do token: aprova a conta filha no Sandbox. */
    public void sandboxAprovarConta() {
        roteadorSandbox.aprovarConta();
    }

    /** FINANCEIRO da escola do token: confirma no Sandbox o pagamento da cobranca emitida. */
    public void sandboxConfirmarPagamento(UUID cobrancaId) {
        roteadorSandbox.confirmarPagamento(cobrancaId);
    }

    private static UnsupportedOperationException naoImplementado(String tarefa) {
        return new UnsupportedOperationException("Ainda nao implementado (tarefa " + tarefa + ")");
    }
}
