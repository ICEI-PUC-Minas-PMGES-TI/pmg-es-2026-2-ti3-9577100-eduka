package br.pucminas.eduka.backend.pagamento;

import java.util.UUID;

import org.springframework.stereotype.Service;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.dto.AtivarRecebimentosRequest;
import br.pucminas.eduka.backend.dto.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.ContaRecebimentoResponse;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.HabilitacaoRecebimentos;
import br.pucminas.eduka.backend.dto.LancarFaturaRequest;
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

    private final ChamadorProvedor provedor;
    private final RoteadorConta roteadorConta;
    private final RoteadorPagamento roteadorPagamento;

    PaymentRoutingService(ChamadorProvedor provedor, RoteadorConta roteadorConta,
            RoteadorPagamento roteadorPagamento) {
        this.provedor = provedor;
        this.roteadorConta = roteadorConta;
        this.roteadorPagamento = roteadorPagamento;
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

    /** Webhook; spec §8.3. */
    public void registrarEvento(String tokenRecebido, String corpoJson) {
        throw naoImplementado("PO.3");
    }

    /** Aplica o evento uma vez, com o tenant da escola dele. */
    public void processarEvento(UUID eventoId) {
        throw naoImplementado("PO.3");
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
