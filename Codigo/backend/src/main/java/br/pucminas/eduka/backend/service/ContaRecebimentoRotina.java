package br.pucminas.eduka.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;

/**
 * Rotina que acompanha a aprovacao das contas de recebimento: le no provedor a
 * situacao das contas AGUARDANDO_APROVACAO ({@code app.pagamento.conta.sincronizacao}).
 * Cobre o webhook que nao chegou (em dev, a URL do tunel muda a cada execucao).
 */
@Component
public class ContaRecebimentoRotina {

    private static final Logger log = LoggerFactory.getLogger(ContaRecebimentoRotina.class);

    private final PaymentRoutingService paymentRoutingService;

    public ContaRecebimentoRotina(PaymentRoutingService paymentRoutingService) {
        this.paymentRoutingService = paymentRoutingService;
    }

    @Scheduled(fixedDelayString = "${app.pagamento.conta.sincronizacao}",
            initialDelayString = "${app.pagamento.conta.sincronizacao}")
    public void sincronizar() {
        int sincronizadas = paymentRoutingService.sincronizarContasAguardando();
        if (sincronizadas > 0) {
            log.info("Rotina de contas: {} conta(s) de recebimento sincronizada(s)", sincronizadas);
        }
    }
}
