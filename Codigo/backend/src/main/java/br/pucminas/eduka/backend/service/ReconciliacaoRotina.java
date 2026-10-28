package br.pucminas.eduka.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;

/**
 * Rotina que consulta o provedor sobre as cobrancas e faturas que ficaram
 * EMITINDO sem resposta (reconciliacao, {@code app.pagamento.reconciliacao.*}).
 * Cobre a criacao cuja resposta e cujo aviso se perderam.
 */
@Component
public class ReconciliacaoRotina {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoRotina.class);

    private final PaymentRoutingService paymentRoutingService;
    private final int lote;

    public ReconciliacaoRotina(PaymentRoutingService paymentRoutingService,
            @Value("${app.pagamento.reconciliacao.lote}") int lote) {
        this.paymentRoutingService = paymentRoutingService;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${app.pagamento.reconciliacao.intervalo}",
            initialDelayString = "${app.pagamento.reconciliacao.intervalo}")
    public void reconciliar() {
        int resolvidas = paymentRoutingService.reconciliarEmissoesPendentes(lote);
        if (resolvidas > 0) {
            log.info("Rotina de reconciliacao: {} emissao(oes) sem resposta resolvida(s)", resolvidas);
        }
    }
}
