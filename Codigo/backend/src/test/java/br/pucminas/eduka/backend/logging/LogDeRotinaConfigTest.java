package br.pucminas.eduka.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class LogDeRotinaConfigTest {

    @Test
    void cadaExecucaoAgendadaGanhaUmRequestIdProprio() throws Exception {
        ThreadPoolTaskScheduler agendador = new ThreadPoolTaskScheduler();
        new LogDeRotinaConfig().logDeRotinaCustomizer().customize(agendador);
        agendador.initialize();
        try {
            CopyOnWriteArrayList<String> vistos = new CopyOnWriteArrayList<>();
            CountDownLatch tresExecucoes = new CountDownLatch(3);

            agendador.scheduleWithFixedDelay(() -> {
                vistos.add(MDC.get(ContextoLog.REQUEST_ID));
                tresExecucoes.countDown();
            }, Duration.ofMillis(10));

            assertThat(tresExecucoes.await(5, TimeUnit.SECONDS)).isTrue();

            // A rotina segue rodando: uma subList da lista viva quebra quando entra a 4a execucao.
            List<String> tresPrimeiros = List.copyOf(vistos).subList(0, 3);
            assertThat(tresPrimeiros).allSatisfy(id -> assertThat(UUID.fromString(id)).isNotNull());
            assertThat(tresPrimeiros.stream().distinct().count()).isEqualTo(3);
        } finally {
            agendador.shutdown();
        }
    }

    @Test
    void mdcSaiLimpoDaRotinaTambemQuandoElaFalha() {
        Runnable rotina = LogDeRotinaConfig.comIdDeExecucao().decorate(() -> {
            ContextoLog.definirEscola(UUID.randomUUID());
            throw new IllegalStateException("rotina quebrou");
        });

        assertThatThrownBy(rotina::run).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(ContextoLog.REQUEST_ID)).isNull();
        assertThat(MDC.get(ContextoLog.ESCOLA_ID)).isNull();
    }
}
