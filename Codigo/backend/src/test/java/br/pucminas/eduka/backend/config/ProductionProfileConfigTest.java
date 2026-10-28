package br.pucminas.eduka.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

/**
 * Garante que o perfil "prod" nao herda os defaults de conveniencia de
 * desenvolvimento. Carrega so os arquivos application*.properties, sem
 * auto-configuracao, entao nao precisa de banco.
 */
class ProductionProfileConfigTest {

    private static final String[] VARIAVEIS_OBRIGATORIAS = {
            "POSTGRES_DB", "DB_SSLMODE", "DB_APP_USER", "DB_APP_PASSWORD" };

    /**
     * O Spring Boot le SPRING_DATASOURCE_* do ambiente por cima de qualquer
     * properties. Se estiverem exportadas (por exemplo, para rodar o
     * contextLoads contra um banco local), as asseroes sobre o que vem do
     * arquivo deixam de fazer sentido.
     */
    private static final String[] OVERRIDES_DE_AMBIENTE = {
            "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD" };

    private static void assumirSemVariaveis(String... variaveis) {
        for (String variavel : variaveis) {
            assumeTrue(System.getenv(variavel) == null,
                    variavel + " esta definida no ambiente; teste nao se aplica");
        }
    }

    private ApplicationContextRunner runnerComPerfil(String perfil) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + perfil);
    }

    @Test
    void devProfileFallsBackToLocalDefaults() {
        runnerComPerfil("default").run(context -> {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("eduka_app");
            assertThat(env.getProperty("spring.jpa.show-sql")).isEqualTo("true");
        });
    }

    @Test
    void prodProfileHasNoDefaultForDatabaseCredentials() {
        // Se a maquina de quem roda o teste tiver essas variaveis exportadas, o
        // placeholder resolve e o cenario "variavel faltando" nao existe.
        assumirSemVariaveis(VARIAVEIS_OBRIGATORIAS);
        assumirSemVariaveis(OVERRIDES_DE_AMBIENTE);

        runnerComPerfil("prod").run(context -> {
            Environment env = context.getEnvironment();

            assertThatThrownBy(() -> env.getProperty("spring.datasource.password"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DB_APP_PASSWORD");
            assertThatThrownBy(() -> env.getProperty("spring.datasource.username"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DB_APP_USER");
            assertThatThrownBy(() -> env.getProperty("spring.datasource.url"))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void prodProfileResolvesCredentialsWhenVariablesArePresent() {
        assumirSemVariaveis(OVERRIDES_DE_AMBIENTE);

        runnerComPerfil("prod")
                .withPropertyValues(
                        "POSTGRES_DB=eduka_prod",
                        "DB_SSLMODE=verify-full",
                        "DB_APP_USER=usuario_prod",
                        "DB_APP_PASSWORD=segredo-prod")
                .run(context -> {
                    Environment env = context.getEnvironment();
                    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("usuario_prod");
                    assertThat(env.getProperty("spring.datasource.password")).isEqualTo("segredo-prod");
                    assertThat(env.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://postgres:5432/eduka_prod?sslmode=verify-full");
                });
    }

    @Test
    void prodProfileDisablesSqlLoggingAndErrorDetails() {
        runnerComPerfil("prod").run(context -> {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.jpa.show-sql")).isEqualTo("false");
            assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");
            assertThat(env.getProperty("server.error.include-message")).isEqualTo("never");
            assertThat(env.getProperty("server.error.include-exception")).isEqualTo("false");
        });
    }

    @Test
    void soODemoLigaOMockEmProd() {
        runnerComPerfil("prod").run(context -> assertThat(
                context.getEnvironment().getProperty("app.mock.habilitado")).isEqualTo("false"));

        runnerComPerfil("prod,demo").run(context -> {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("app.mock.habilitado")).isEqualTo("true");
            // Sem default: a senha do mock e a do acesso nunca sao as do README.
            assertThatThrownBy(() -> env.getProperty("app.mock.senha"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MOCK_SENHA");
            assertThatThrownBy(() -> env.getProperty("app.demo.acesso.senha"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DEMO_ACESSO_SENHA");
        });
    }

    @Test
    void prodProfileStillValidatesSchemaInsteadOfAlteringIt() {
        runnerComPerfil("prod").run(context -> assertThat(
                context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate"));
    }
}
