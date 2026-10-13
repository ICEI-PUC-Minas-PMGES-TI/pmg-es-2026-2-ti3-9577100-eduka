package br.pucminas.eduka.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina diaria que grava EM_ATRASO nas cobrancas vencidas. Roda logo depois
 * da virada do dia no fuso da escola ({@code app.cobranca.atraso.cron}).
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

    public CobrancaAtrasoRotina(CobrancaService cobrancaService) {
        this.cobrancaService = cobrancaService;
    }

    @Scheduled(cron = "${app.cobranca.atraso.cron}", zone = "America/Sao_Paulo")
    public void marcarVencidas() {
        int marcadas = cobrancaService.marcarVencidasComoEmAtraso();
        log.info("Rotina de atraso: {} cobranca(s) marcada(s) como EM_ATRASO", marcadas);
    }
}
