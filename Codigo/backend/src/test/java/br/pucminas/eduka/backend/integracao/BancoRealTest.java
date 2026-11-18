package br.pucminas.eduka.backend.integracao;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Base dos testes de integracao com Postgres de verdade. Os demais testes
 * trocam os repositorios por mock e nunca chegam ao banco: migration que nao
 * roda no Postgres, entidade que nao bate com a tabela, query nativa errada e
 * constraint que deveria barrar duplicata so aparecem aqui.
 *
 * <p>
 * O banco imita o de producao: a mesma imagem do {@code docker-compose.yml} e
 * o mesmo script de {@code postgres/init}, entao a aplicacao (e o Flyway)
 * conecta como o papel restrito {@code DB_APP_USER}, nao como superusuario.
 * Sobe a aplicacao inteira, com Flyway e {@code ddl-auto=validate}.
 * </p>
 *
 * <p>
 * Um container so para todas as classes filhas: ele sobe na primeira e o
 * Testcontainers o derruba no fim da execucao. Como todas usam a mesma
 * configuracao, o Spring tambem reaproveita o contexto. Os dados de um teste
 * ficam no banco para os seguintes; cada teste cria a propria escola (ver
 * {@link DadosDeTeste}) em vez de contar com o banco vazio.
 * </p>
 *
 * <p>
 * Sem Docker, as classes filhas sao puladas em vez de falhar.
 * </p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class BancoRealTest {

    /** Os defaults de {@code application.properties}; o banco so existe durante o teste. */
    private static final String USUARIO_APP = "eduka_app";
    private static final String SENHA_APP = "changeme-app";

    // Fica de pe a execucao inteira e o Testcontainers o derruba no fim: nao ha o que fechar aqui.
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withEnv("DB_APP_USER", USUARIO_APP)
            .withEnv("DB_APP_PASSWORD", SENHA_APP)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "postgres", "init", "01-create-app-role.sh"), 0755),
                    "/docker-entrypoint-initdb.d/01-create-app-role.sh");

    /**
     * Sobe o container aqui, e nao num bloco static, para que a checagem do
     * Docker em {@link Testcontainers#disabledWithoutDocker} rode antes.
     */
    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> USUARIO_APP);
        registry.add("spring.datasource.password", () -> SENHA_APP);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private PiiCryptoService piiCryptoService;

    protected DadosDeTeste dados;

    @BeforeEach
    void prepararDados() {
        dados = new DadosDeTeste(jdbc, piiCryptoService);
    }

    @AfterEach
    void sairDoUsuario() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Poe um usuario no {@code SecurityContext}, como o filtro do JWT faz numa
     * requisicao: a partir dai o {@code @TenantId} filtra pela {@code escolaId}
     * e os services enxergam esse usuario. Sem chamar, o teste roda como o
     * webhook e as rotinas, sem ninguem autenticado.
     */
    protected static UsuarioAutenticado autenticarComo(String perfil, UUID escolaId) {
        return autenticarComo(UUID.randomUUID(), perfil, escolaId);
    }

    /**
     * Cria um USUARIO do perfil na escola e entra com ele: para os services
     * que auditam, ja que o log aponta para o usuario.
     */
    protected UsuarioAutenticado entrarComo(String perfil, UUID escolaId) {
        return autenticarComo(dados.usuario(escolaId, perfil, "Usuario " + perfil), perfil, escolaId);
    }

    /**
     * Com o id de um USUARIO gravado: precisa quando o service audita (o log
     * aponta para o usuario) ou procura o aluno pelo login.
     */
    protected static UsuarioAutenticado autenticarComo(UUID usuarioId, String perfil, UUID escolaId) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(usuarioId, perfil, escolaId);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
        return usuario;
    }
}
