package br.pucminas.eduka.backend.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.UsuarioService;

/**
 * Acesso de demonstracao: quem sabe o login e a senha de
 * {@link DemoAcessoProperties} ve as contas de teste e entra em qualquer uma
 * com um clique, sem a senha dela e sem o codigo por e-mail.
 *
 * <p>
 * So existe no perfil {@code demo} (ambiente de apresentacao, junto com
 * {@code prod}). So entra nas contas da lista abaixo: a escola de
 * demonstracao do MockDataSeeder e o super admin inicial
 * (SuperAdminInicialSeeder). Qualquer outro usuario do banco fica de fora.
 * </p>
 */
@Service
@Profile("demo")
@EnableConfigurationProperties(DemoAcessoProperties.class)
public class DemoAcessoService {

    public record Conta(PerfilUsuario perfil, String rotulo, String login) {
    }

    public record Grupo(String titulo, String descricao, List<Conta> contas) {
    }

    /** As mesmas contas da tela /login/dev, todas criadas pelo MockDataSeeder. */
    private static final Grupo ESCOLA_DEMO = new Grupo("Colégio Ipê Amarelo",
            "Escola de demonstração: 50 alunos, turmas, notas e financeiro.", List.of(
                    new Conta(PerfilUsuario.MASTER, "Master · Helena Duarte", "helena.duarte@mock.eduka.com.br"),
                    new Conta(PerfilUsuario.SECRETARIA, "Secretaria · Patrícia Andrade",
                            "patricia.andrade@mock.eduka.com.br"),
                    new Conta(PerfilUsuario.FINANCEIRO, "Financeiro · Marcos Teixeira",
                            "marcos.teixeira@mock.eduka.com.br"),
                    new Conta(PerfilUsuario.PROFESSOR, "Professora · Ana Paula Ribeiro",
                            "ana.ribeiro@mock.eduka.com.br"),
                    new Conta(PerfilUsuario.ALUNO, "Aluna · Laura Fernandes (6º ano)",
                            "laura.fernandes@mock.eduka.com.br")));

    private static final Logger log = LoggerFactory.getLogger(DemoAcessoService.class);

    private final String loginAcesso;
    private final String senhaAcesso;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final String loginSuperAdmin;

    public DemoAcessoService(DemoAcessoProperties acesso, UsuarioRepository usuarioRepository,
            JwtService jwtService, @Value("${app.super-admin.login:}") String loginSuperAdmin) {
        // Espaco ou quebra de linha nas pontas e comum ao colar no painel da hospedagem.
        this.loginAcesso = normalizarAcesso(exigirDefinida(acesso.login(), "DEMO_ACESSO_LOGIN"));
        this.senhaAcesso = exigirDefinida(acesso.senha(), "DEMO_ACESSO_SENHA").strip();
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.loginSuperAdmin = loginSuperAdmin.isBlank() ? "" : UsuarioService.normalizarLogin(loginSuperAdmin);
        // So o tamanho da senha: confere a configuracao sem expor o valor no log.
        log.info("Acesso de demonstracao: login {}, senha de {} caracteres", loginAcesso, senhaAcesso.length());
    }

    /** Contas disponiveis, so as que existem e estao ativas no banco. */
    public List<Grupo> contas(String login, String senha) {
        exigirAcesso(login, senha);
        return grupos().stream()
                .map(grupo -> new Grupo(grupo.titulo(), grupo.descricao(), grupo.contas().stream()
                        .filter(conta -> usuarioAtivo(conta.login()).isPresent())
                        .toList()))
                .filter(grupo -> !grupo.contas().isEmpty())
                .toList();
    }

    /** Tokens da conta escolhida, como se ela tivesse feito o login. */
    public LoginResponse entrar(String login, String senha, String conta) {
        exigirAcesso(login, senha);
        String loginConta = UsuarioService.normalizarLogin(conta);
        boolean daLista = grupos().stream()
                .flatMap(grupo -> grupo.contas().stream())
                .anyMatch(c -> c.login().equals(loginConta));
        Usuario usuario = (daLista ? usuarioAtivo(loginConta) : Optional.<Usuario>empty())
                .orElseThrow(CredenciaisInvalidasException::new);
        return LoginResponse.autenticado(
                jwtService.generateAccessToken(usuario.getId(), usuario.getPerfil().name(), usuario.getEscolaId()),
                jwtService.generateRefreshToken(usuario.getId()),
                usuario.getId(),
                usuario.getPerfil());
    }

    private List<Grupo> grupos() {
        if (loginSuperAdmin.isEmpty()) {
            return List.of(ESCOLA_DEMO);
        }
        Grupo plataforma = new Grupo("Plataforma Eduka", "Console do super admin: escolas, planos e faturas.",
                List.of(new Conta(PerfilUsuario.SUPER_ADMIN, "Super Admin", loginSuperAdmin)));
        return List.of(ESCOLA_DEMO, plataforma);
    }

    private Optional<Usuario> usuarioAtivo(String login) {
        return usuarioRepository.findByLogin(login).filter(Usuario::isAtivo);
    }

    /** Comparacao em tempo constante, para o tempo de resposta nao entregar a senha aos poucos. */
    private void exigirAcesso(String login, String senha) {
        boolean loginConfere = iguais(normalizarAcesso(login), loginAcesso);
        boolean senhaConfere = iguais(senha == null ? "" : senha.strip(), senhaAcesso);
        if (!loginConfere || !senhaConfere) {
            throw new CredenciaisInvalidasException();
        }
    }

    /**
     * O binder das @ConfigurationProperties nao falha com variavel de ambiente
     * ausente: guarda o proprio "${VARIAVEL}" como valor. Sem esta conferencia,
     * o acesso subiria com uma senha que ninguem sabe.
     */
    private static String exigirDefinida(String valor, String variavel) {
        if (valor == null || valor.isBlank() || valor.contains("${")) {
            throw new IllegalStateException(variavel + " nao definida: o perfil demo precisa dela");
        }
        return valor;
    }

    private static String normalizarAcesso(String login) {
        return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean iguais(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
