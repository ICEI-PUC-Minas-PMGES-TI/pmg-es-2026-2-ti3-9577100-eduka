package br.pucminas.eduka.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** O log em JSON vale em todos os perfis, dev inclusive; nenhum perfil o desliga. */
class LogEstruturadoConfigTest {

    @ParameterizedTest
    @ValueSource(strings = { "default", "prod", "demo", "prod,demo" })
    void logEmJsonEmTodosOsPerfis(String perfil) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + perfil)
                .run(context -> assertThat(
                        context.getEnvironment().getProperty("logging.structured.format.console"))
                        .isEqualTo("ecs"));
    }
}
