package br.pucminas.eduka.backend.email;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.domain.StatusNotificacao;
import br.pucminas.eduka.backend.repository.NotificacaoRepository;

/**
 * Reprocessa a fila de notificacoes a cada {@code app.notificacao.intervalo}:
 * pega as pendentes que ja podem ser tentadas (as que falharam na tentativa
 * imediata, ou que ficaram para tras se a aplicacao caiu entre o commit e o
 * envio) e tenta cada uma na propria transacao.
 */
@Component
public class NotificacaoRotina {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoRotina.class);

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoProcessador processador;
    private final NotificacaoProperties properties;
    private final Clock clock;

    public NotificacaoRotina(NotificacaoRepository notificacaoRepository, NotificacaoProcessador processador,
            NotificacaoProperties properties, Clock clock) {
        this.notificacaoRepository = notificacaoRepository;
        this.processador = processador;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return quantas notificacoes foram tentadas */
    @Scheduled(fixedDelayString = "${app.notificacao.intervalo}", initialDelayString = "${app.notificacao.intervalo}")
    public int processarFila() {
        List<UUID> ids = notificacaoRepository.buscarIdsProntas(StatusNotificacao.PENDENTE, clock.instant(),
                PageRequest.of(0, properties.lote()));
        for (UUID id : ids) {
            try {
                processador.processar(id);
            } catch (RuntimeException ex) {
                // Erro de banco numa notificacao nao para o lote; ela volta na proxima execucao.
                log.error("Erro ao processar a notificacao {} ({})", id, ex.getClass().getSimpleName());
            }
        }
        if (!ids.isEmpty()) {
            log.info("Fila de notificacoes: {} tentativa(s) de envio", ids.size());
        }
        return ids.size();
    }
}
