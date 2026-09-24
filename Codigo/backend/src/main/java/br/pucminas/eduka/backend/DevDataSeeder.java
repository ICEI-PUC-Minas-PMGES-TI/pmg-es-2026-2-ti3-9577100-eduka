package br.pucminas.eduka.backend;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;

/**
 * Cria o super admin de dev (login admin@eduka.com.br / Eduka-admin123) via
 * JPA, nao SQL cru: PESSOA.NOME/EMAIL precisam passar pelo
 * PessoaCryptoConverter para serem cifrados de verdade. O resto do mock
 * (escolas) continua em postgres/mock/mock.sql, que nao mexe em PESSOA.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final String LOGIN_SUPER_ADMIN = "admin@eduka.com.br";
    private static final String SENHA_SUPER_ADMIN = "Eduka-admin123";

    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)) {
            return;
        }
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .nome("Super Admin")
                .email(LOGIN_SUPER_ADMIN)
                .ativo(true)
                .build());
        pessoaPapelRepository.save(PessoaPapel.builder()
                .pessoaId(pessoa.getId())
                .papel(PapelPessoa.USUARIO)
                .build());
        usuarioRepository.save(Usuario.builder()
                .pessoaId(pessoa.getId())
                .login(LOGIN_SUPER_ADMIN)
                .senhaHash(passwordEncoder.encode(SENHA_SUPER_ADMIN))
                .perfil(PerfilUsuario.SUPER_ADMIN)
                .ativo(true)
                .build());
    }
}
