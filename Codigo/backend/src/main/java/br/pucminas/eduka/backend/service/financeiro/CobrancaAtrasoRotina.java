package br.pucminas.eduka.backend.service.financeiro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;

/**
 * Rotina diaria que grava EM_ATRASO nas cobrancas e nas faturas da plataforma
 * vencidas. Roda logo depois da virada do dia no fuso da escola
 * ({@code app.cobranca.atraso.cron}).
 *
 * <p>
 * Se a aplicacao estiver fora do ar no horario, nada se perde: a leitura ja
 * mostra a pendente vencida como em atraso, e a proxima execucao grava todas
 * as que ficaram para tras.
 * </p>
 */
@Component
public class CobrancaAtrasoRotina {

    private static final Logger log = LoggerFactory.getLogger(CobrancaAtrasoRotina.class);

    private final CobrancaService cobrancaService;
    private final PaymentRoutingService paymentRoutingService;

    public CobrancaAtrasoRotina(CobrancaService cobrancaService, PaymentRoutingService paymentRoutingService) {
        this.cobrancaService = cobrancaService;
        this.paymentRoutingService = paymentRoutingService;
    }

    @Scheduled(cron = "${app.cobranca.atraso.cron}", zone = "America/Sao_Paulo")
    public void marcarVencidas() {
        int marcadas = cobrancaService.marcarVencidasComoEmAtraso();
        int faturas = paymentRoutingService.marcarFaturasVencidas();
        log.info("Rotina de atraso: {} cobranca(s) e {} fatura(s) marcada(s) como EM_ATRASO", marcadas, faturas);
    }
}
