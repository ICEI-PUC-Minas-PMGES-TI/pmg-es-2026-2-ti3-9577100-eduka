package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.exception.AcessoJaAtivadoException;
import br.pucminas.eduka.backend.exception.LoginJaCadastradoException;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.HierarquiaPerfis;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PerfilNaoPermitidoException;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Login de quem e cadastrado pela escola (professor hoje; depois aluno,
 * funcionario...). Serve a qualquer perfil: quem chama passa a {@link Pessoa}
 * ja cadastrada e o perfil do novo usuario.
 *
 * <p>
 * O login e o e-mail da pessoa e a senha provisoria e o CPF, que so vale ate
 * {@code app.acesso.senha-provisoria-validade-dias} e nao abre o sistema: o
 * primeiro login so permite trocar pela senha definitiva (ver
 * {@link AuthService#definirSenhaDefinitiva}).
 * </p>
 */
@Service
public class AcessoService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final IsolamentoEscola isolamentoEscola;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Duration validadeSenhaProvisoria;

    public AcessoService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            IsolamentoEscola isolamentoEscola, ApplicationEventPublisher eventos, Clock clock,
            @Value("${app.acesso.senha-provisoria-validade-dias}") long validadeDias) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.isolamentoEscola = isolamentoEscola;
        this.eventos = eventos;
        this.clock = clock;
        this.validadeSenhaProvisoria = Duration.ofDays(validadeDias);
    }

    /**
     * Cria o usuario com senha provisoria e agenda o e-mail. Se a pessoa ja
     * tem usuario que ainda nao trocou a senha, e um reenvio: senha e validade
     * sao renovadas e o login acompanha o e-mail atual.
     *
     * @throws AcessoJaAtivadoException se o usuario ja definiu a senha definitiva
     */
    @Transactional
    public Usuario enviarAcessoInicial(Pessoa pessoa, PerfilUsuario perfil) {
        exigirPermissao(perfil);
        String email = obrigatorio(pessoa.getEmail(), "E-mail e obrigatorio para criar o acesso");
        String cpf = obrigatorio(pessoa.getCpf(), "CPF e obrigatorio para criar o acesso");
        String login = UsuarioService.normalizarLogin(email);

        Usuario usuario = usuarioRepository.findByPessoaId(pessoa.getId()).orElse(null);
        if (usuario == null) {
            exigirLoginLivre(login);
            usuario = Usuario.builder()
                    .escolaId(pessoa.getEscolaId())
                    .pessoaId(pessoa.getId())
                    .login(login)
                    .perfil(perfil)
                    .ativo(true)
                    .build();
        } else if (!usuario.isSenhaProvisoria()) {
            throw new AcessoJaAtivadoException();
        } else {
            trocarLogin(usuario, login);
        }

        Instant expiraEm = clock.instant().plus(validadeSenhaProvisoria);
        usuario.setSenhaHash(passwordEncoder.encode(cpf));
        usuario.setSenhaProvisoria(true);
        usuario.setSenhaProvisoriaExpiraEm(expiraEm);
        usuario = usuarioRepository.save(usuario);

        eventos.publishEvent(new AcessoInicialCriado(usuario.getId(), pessoa.getNome(), email, login, expiraEm));
        return usuario;
    }

    /** Login e o e-mail: quando o e-mail da pessoa muda, o login muda junto. */
    @Transactional
    public void sincronizarLogin(Pessoa pessoa) {
        if (pessoa.getEmail() == null || pessoa.getEmail().isBlank()) {
            return;
        }
        String login = UsuarioService.normalizarLogin(pessoa.getEmail());
        usuarioRepository.findByPessoaId(pessoa.getId()).ifPresent(usuario -> {
            if (trocarLogin(usuario, login)) {
                usuarioRepository.save(usuario);
            }
        });
    }

    /** Pessoa inativada perde o login; reativada, recupera. */
    @Transactional
    public void definirAtivo(Pessoa pessoa, boolean ativo) {
        usuarioRepository.findByPessoaId(pessoa.getId()).ifPresent(usuario -> {
            if (usuario.isAtivo() != ativo) {
                usuario.setAtivo(ativo);
                usuarioRepository.save(usuario);
            }
        });
    }

    private boolean trocarLogin(Usuario usuario, String login) {
        if (login.equals(usuario.getLogin())) {
            return false;
        }
        exigirLoginLivre(login);
        usuario.setLogin(login);
        return true;
    }

    private void exigirLoginLivre(String login) {
        if (usuarioRepository.existsByLogin(login)) {
            throw new LoginJaCadastradoException(login);
        }
    }

    private void exigirPermissao(PerfilUsuario perfil) {
        UsuarioAutenticado criador = isolamentoEscola.usuarioAtual();
        if (!HierarquiaPerfis.podeCriar(criador.perfilUsuario(), perfil)) {
            throw new PerfilNaoPermitidoException(
                    "%s nao pode criar usuario %s".formatted(criador.perfil(), perfil));
        }
    }

    private static String obrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor.trim();
    }
}
