package br.pucminas.eduka.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.service.UsuarioService;

/**
 * Cria o primeiro SUPER_ADMIN a partir de {@code SUPER_ADMIN_LOGIN} e
 * {@code SUPER_ADMIN_SENHA}, em qualquer perfil. Sem isso o banco de producao
 * nasce sem nenhum usuario e ninguem consegue entrar (o DevDataSeeder so roda
 * em dev).
 *
 * <p>
 * Sem as duas variaveis, nao faz nada. Se o login ja existe, tambem nao: a
 * senha trocada depois pela tela nunca e sobrescrita por um restart.
 * </p>
 */
@Component
@Order(0)
public class SuperAdminInicialSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInicialSeeder.class);

    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final PasswordEncoder passwordEncoder;
    private final String login;
    private final String senha;

    public SuperAdminInicialSeeder(UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, PasswordEncoder passwordEncoder,
            @Value("${app.super-admin.login:}") String login, @Value("${app.super-admin.senha:}") String senha) {
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.passwordEncoder = passwordEncoder;
        this.login = login;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (login.isBlank() || senha.isBlank()) {
            return;
        }
        String loginNormalizado = UsuarioService.normalizarLogin(login);
        if (usuarioRepository.existsByLogin(loginNormalizado)) {
            return;
        }
        // Senha fora da regra derruba a subida: melhor que um admin que nao entra.
        UsuarioService.validarSenha(senha);

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .nome("Super Admin")
                .email(loginNormalizado)
                .ativo(true)
                .build());
        pessoaPapelRepository.save(PessoaPapel.builder()
                .pessoaId(pessoa.getId())
                .papel(PapelPessoa.USUARIO)
                .build());
        usuarioRepository.save(Usuario.builder()
                .pessoaId(pessoa.getId())
                .login(loginNormalizado)
                .senhaHash(passwordEncoder.encode(senha))
                .perfil(PerfilUsuario.SUPER_ADMIN)
                .ativo(true)
                .build());
        // Sem o login (e-mail do super admin): so avisa que o primeiro acesso foi criado.
        log.info("Super admin inicial criado");
    }
}
