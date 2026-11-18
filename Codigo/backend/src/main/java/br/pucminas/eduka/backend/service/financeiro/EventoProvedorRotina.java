package br.pucminas.eduka.backend.service.financeiro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;

/**
 * Rotina que reaplica os avisos do provedor que nao terminaram: falharam ao
 * aplicar (ERRO) ou foram interrompidos no meio ({@code app.pagamento.eventos.*}).
 * O aviso que chega a {@code max-tentativas} fica em ERRO para analise manual.
 */
@Component
public class EventoProvedorRotina {

    private static final Logger log = LoggerFactory.getLogger(EventoProvedorRotina.class);

    private final PaymentRoutingService paymentRoutingService;
    private final int maxTentativas;
    private final int lote;

    public EventoProvedorRotina(PaymentRoutingService paymentRoutingService,
            @Value("${app.pagamento.eventos.max-tentativas}") int maxTentativas,
            @Value("${app.pagamento.eventos.lote}") int lote) {
        this.paymentRoutingService = paymentRoutingService;
        this.maxTentativas = maxTentativas;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${app.pagamento.eventos.reprocessamento}",
            initialDelayString = "${app.pagamento.eventos.reprocessamento}")
    public void reprocessar() {
        int reaplicados = paymentRoutingService.processarEventosPendentes(maxTentativas, lote);
        if (reaplicados > 0) {
            log.info("Rotina de avisos: {} aviso(s) do provedor reaplicado(s)", reaplicados);
        }
    }
}
