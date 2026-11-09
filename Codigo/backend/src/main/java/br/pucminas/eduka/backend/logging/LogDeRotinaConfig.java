package br.pucminas.eduka.backend.logging;

import org.slf4j.MDC;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * Da um {@code requestId} novo a cada execucao de qualquer {@code @Scheduled},
 * para as linhas de log de uma rodada da rotina se agruparem como as de uma
 * requisicao. Fica no agendador, e nao em cada rotina, para que a rotina nova
 * ja nasca com o id.
 */
@Configuration
public class LogDeRotinaConfig {

    @Bean
    public ThreadPoolTaskSchedulerCustomizer logDeRotinaCustomizer() {
        return scheduler -> scheduler.setTaskDecorator(comIdDeExecucao());
    }

    static TaskDecorator comIdDeExecucao() {
        return tarefa -> () -> {
            MDC.put(ContextoLog.REQUEST_ID, ContextoLog.novoId());
            try {
                tarefa.run();
            } finally {
                ContextoLog.limpar();
            }
        };
    }
}
