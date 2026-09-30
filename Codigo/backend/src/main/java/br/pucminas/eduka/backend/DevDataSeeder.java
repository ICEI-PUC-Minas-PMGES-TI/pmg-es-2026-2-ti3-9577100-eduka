package br.pucminas.eduka.backend;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.PlanoEscola;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;

/**
 * Cria os usuarios de dev via JPA, nao SQL cru: PESSOA.NOME/EMAIL precisam
 * passar pelo PessoaCryptoConverter para serem cifrados de verdade. O resto do
 * mock (escolas) continua em postgres/mock/mock.sql, que nao mexe em PESSOA.
 *
 * <ul>
 * <li>Super admin: admin@eduka.com.br / Eduka-admin123</li>
 * <li>Um usuario de cada perfil de escola, todos na "Escola Dev" e com a senha
 * Eduka-dev123: master, secretaria, financeiro, professor e aluno
 * {@code @dev.eduka.com.br}. A tela /login/dev do front entra com eles.</li>
 * </ul>
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final String LOGIN_SUPER_ADMIN = "admin@eduka.com.br";
    private static final String SENHA_SUPER_ADMIN = "Eduka-admin123";

    private static final String NOME_ESCOLA_DEV = "Escola Dev";
    private static final String SENHA_DEV = "Eduka-dev123";

    /** Perfil -> login, na ordem em que aparecem na tela /login/dev. */
    private static final Map<PerfilUsuario, String> LOGINS_DEV = new LinkedHashMap<>();

    static {
        LOGINS_DEV.put(PerfilUsuario.MASTER, "master@dev.eduka.com.br");
        LOGINS_DEV.put(PerfilUsuario.SECRETARIA, "secretaria@dev.eduka.com.br");
        LOGINS_DEV.put(PerfilUsuario.FINANCEIRO, "financeiro@dev.eduka.com.br");
        LOGINS_DEV.put(PerfilUsuario.PROFESSOR, "professor@dev.eduka.com.br");
        LOGINS_DEV.put(PerfilUsuario.ALUNO, "aluno@dev.eduka.com.br");
    }

    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final EscolaRepository escolaRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, EscolaRepository escolaRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.escolaRepository = escolaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)) {
            criarUsuario("Super Admin", LOGIN_SUPER_ADMIN, SENHA_SUPER_ADMIN, PerfilUsuario.SUPER_ADMIN, null);
        }

        UUID escolaDevId = null;
        for (Map.Entry<PerfilUsuario, String> entrada : LOGINS_DEV.entrySet()) {
            PerfilUsuario perfil = entrada.getKey();
            String login = entrada.getValue();
            if (usuarioRepository.existsByLogin(login)) {
                continue;
            }
            if (escolaDevId == null) {
                escolaDevId = escolaDevId();
            }
            criarUsuario(nomeDev(perfil), login, SENHA_DEV, perfil, escolaDevId);
        }
    }

    /** Reaproveita a escola de um usuario dev ja criado; senao cria a "Escola Dev". */
    private UUID escolaDevId() {
        return LOGINS_DEV.values().stream()
                .map(usuarioRepository::findByLogin)
                .flatMap(Optional::stream)
                .map(Usuario::getEscolaId)
                .findFirst()
                .orElseGet(() -> escolaRepository.save(Escola.cadastrar(NOME_ESCOLA_DEV, null, null,
                        "contato@dev.eduka.com.br", null, PlanoEscola.ENTERPRISE, true)).getId());
    }

    private void criarUsuario(String nome, String login, String senha, PerfilUsuario perfil, UUID escolaId) {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(nome)
                .email(login)
                .ativo(true)
                .build());
        pessoaPapelRepository.save(PessoaPapel.builder()
                .pessoaId(pessoa.getId())
                .papel(papelDe(perfil))
                .build());
        usuarioRepository.save(Usuario.builder()
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .login(login)
                .senhaHash(passwordEncoder.encode(senha))
                .perfil(perfil)
                .ativo(true)
                .build());
    }

    private static PapelPessoa papelDe(PerfilUsuario perfil) {
        return switch (perfil) {
            case ALUNO -> PapelPessoa.ALUNO;
            case PROFESSOR -> PapelPessoa.PROFESSOR;
            case SUPER_ADMIN -> PapelPessoa.USUARIO;
            default -> PapelPessoa.FUNCIONARIO;
        };
    }

    private static String nomeDev(PerfilUsuario perfil) {
        String nome = perfil.name().charAt(0) + perfil.name().substring(1).toLowerCase();
        return nome + " Dev";
    }
}
