package br.pucminas.eduka.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina diaria que manda o lembrete das cobrancas que vencem nos proximos
 * dias ({@code app.cobranca.lembrete.cron}).
 *
 * <p>
 * Cada cobranca e avisada uma vez por vencimento (ver
 * {@link CobrancaService#avisarVencimentosProximos()}): se a aplicacao estiver
 * fora do ar no horario, a execucao seguinte avisa as que ficaram para tras,
 * desde que ainda nao tenham vencido.
 * </p>
 */
@Component
public class CobrancaVencimentoRotina {

    private static final Logger log = LoggerFactory.getLogger(CobrancaVencimentoRotina.class);

    private final CobrancaService cobrancaService;

    public CobrancaVencimentoRotina(CobrancaService cobrancaService) {
        this.cobrancaService = cobrancaService;
    }

    @Scheduled(cron = "${app.cobranca.lembrete.cron}", zone = "America/Sao_Paulo")
    public void avisar() {
        int avisos = cobrancaService.avisarVencimentosProximos();
        log.info("Rotina de vencimento: {} lembrete(s) de cobranca enviado(s)", avisos);
    }
}
