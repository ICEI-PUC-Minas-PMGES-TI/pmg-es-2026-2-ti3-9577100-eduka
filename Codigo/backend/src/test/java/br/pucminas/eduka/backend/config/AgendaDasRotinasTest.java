package br.pucminas.eduka.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.SimpleTriggerContext;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.service.escola.SuspensaoPorAtrasoRotina;
import br.pucminas.eduka.backend.service.escola.SuspensaoPorAtrasoService;
import br.pucminas.eduka.backend.service.financeiro.CobrancaAtrasoRotina;
import br.pucminas.eduka.backend.service.financeiro.CobrancaService;
import br.pucminas.eduka.backend.service.financeiro.ContaRecebimentoRotina;
import br.pucminas.eduka.backend.service.financeiro.EventoProvedorRotina;
import br.pucminas.eduka.backend.service.financeiro.ReconciliacaoRotina;

/**
 * Quando cada rotina roda, com os application*.properties de verdade: o
 * {@code @EnableScheduling} registra a agenda como na aplicacao, mas o
 * agendador e um mock, entao nenhuma rotina dispara durante o teste. O que
 * cada uma roda e o que acontece se falhar fica no teste de cada rotina.
 */
class AgendaDasRotinasTest {

    /** 00:00 de 10/10/2026 em Sao Paulo (UTC-3, sem horario de verao). */
    private static final Instant MEIA_NOITE_SP = Instant.parse("2026-10-10T03:00:00Z");

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import({ CobrancaAtrasoRotina.class, SuspensaoPorAtrasoRotina.class, ReconciliacaoRotina.class,
            EventoProvedorRotina.class, ContaRecebimentoRotina.class })
    static class Rotinas {
    }

    private ApplicationContextRunner runnerComPerfil(String perfil) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + perfil)
                .withUserConfiguration(Rotinas.class)
                .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class))
                .withBean(CobrancaService.class, () -> mock(CobrancaService.class))
                .withBean(SuspensaoPorAtrasoService.class, () -> mock(SuspensaoPorAtrasoService.class))
                .withBean(PaymentRoutingService.class, () -> mock(PaymentRoutingService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "default", "prod" })
    void suspensaoRodaTodoDiaAs0030DeBrasiliaDepoisDaRotinaDeAtraso(String perfil) {
        runnerComPerfil(perfil).run(contexto -> {
            CronTask atraso = (CronTask) tarefa(contexto, CobrancaAtrasoRotina.class, "marcarVencidas");
            CronTask suspensao = (CronTask) tarefa(contexto, SuspensaoPorAtrasoRotina.class, "executar");

            // No fuso da escola: em UTC, 00:30 seria 21:30 da vespera em Brasilia.
            assertThat(proxima(suspensao, MEIA_NOITE_SP)).isEqualTo("2026-10-10T03:30:00Z");
            // So avisa e suspende depois que a fatura vencida foi gravada como EM_ATRASO.
            assertThat(proxima(atraso, MEIA_NOITE_SP)).isEqualTo("2026-10-10T03:05:00Z");
            // Uma vez por dia: depois da rodada, so na noite seguinte.
            assertThat(proxima(suspensao, Instant.parse("2026-10-10T03:30:00Z")))
                    .isEqualTo("2026-10-11T03:30:00Z");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "default", "prod" })
    void rotinasDoProvedorRodamEmIntervalosEEsperamUmIntervaloAntesDaPrimeira(String perfil) {
        runnerComPerfil(perfil).run(contexto -> {
            assertIntervalo(tarefa(contexto, ReconciliacaoRotina.class, "reconciliar"), Duration.ofMinutes(1));
            assertIntervalo(tarefa(contexto, EventoProvedorRotina.class, "reprocessar"), Duration.ofMinutes(5));
            assertIntervalo(tarefa(contexto, ContaRecebimentoRotina.class, "sincronizar"), Duration.ofMinutes(15));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "default", "prod" })
    void rotinasDoProvedorUsamOLoteEAsTentativasDaConfiguracao(String perfil) {
        runnerComPerfil(perfil).run(contexto -> {
            PaymentRoutingService paymentRoutingService = contexto.getBean(PaymentRoutingService.class);

            contexto.getBean(ReconciliacaoRotina.class).reconciliar();
            contexto.getBean(EventoProvedorRotina.class).reprocessar();

            verify(paymentRoutingService).reconciliarEmissoesPendentes(50);
            verify(paymentRoutingService).processarEventosPendentes(10, 50);
        });
    }

    /** Tarefa registrada para o metodo {@code @Scheduled} da rotina. */
    private static Task tarefa(AssertableApplicationContext contexto, Class<?> rotina, String metodo) {
        assertThat(contexto).hasNotFailed();
        // O Task se apresenta pelo nome qualificado do metodo agendado.
        String nome = rotina.getName() + "." + metodo;
        return contexto.getBean(ScheduledTaskHolder.class).getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(tarefa -> tarefa.toString().equals(nome))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nenhuma tarefa agendada para " + nome));
    }

    /** Proxima execucao da tarefa a partir de {@code agora}, pelo gatilho que o agendador recebe. */
    private static Instant proxima(CronTask tarefa, Instant agora) {
        return tarefa.getTrigger().nextExecution(new SimpleTriggerContext(Clock.fixed(agora, ZoneOffset.UTC)));
    }

    private static void assertIntervalo(Task tarefa, Duration intervalo) {
        assertThat(tarefa).isInstanceOf(FixedDelayTask.class);
        FixedDelayTask fixa = (FixedDelayTask) tarefa;
        assertThat(fixa.getIntervalDuration()).isEqualTo(intervalo);
        // Nao roda durante a subida: a primeira execucao espera um intervalo.
        assertThat(fixa.getInitialDelayDuration()).isEqualTo(intervalo);
    }
}
