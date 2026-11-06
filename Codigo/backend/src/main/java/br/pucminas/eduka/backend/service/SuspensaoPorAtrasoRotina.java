package br.pucminas.eduka.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina diaria da suspensao por fatura em atraso ({@code app.assinatura.suspensao.cron}),
 * depois da que grava EM_ATRASO. Primeiro suspende as escolas cujo prazo do
 * aviso acabou, depois avisa as faturas que acabaram de passar da tolerancia.
 *
 * <p>
 * Se a aplicacao estiver fora do ar no horario, a proxima execucao pega tudo o
 * que ficou para tras.
 * </p>
 */
@Component
public class SuspensaoPorAtrasoRotina {

    private static final Logger log = LoggerFactory.getLogger(SuspensaoPorAtrasoRotina.class);

    private final SuspensaoPorAtrasoService service;

    public SuspensaoPorAtrasoRotina(SuspensaoPorAtrasoService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.assinatura.suspensao.cron}", zone = "America/Sao_Paulo")
    public void executar() {
        int suspensas = service.suspender();
        int avisadas = service.avisar();
        log.info("Rotina de suspensao: {} escola(s) suspensa(s), {} aviso(s) de suspensao", suspensas, avisadas);
    }
}
