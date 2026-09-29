package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.exception.SenhaInvalidaException;
import br.pucminas.eduka.backend.exception.SenhaProvisoriaExpiradaException;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * Login (RF03). A senha recebida nunca e comparada com a coluna diretamente:
 * passa sempre por {@link PasswordEncoder#matches}.
 *
 * <p>
 * Usuario com senha provisoria (ver {@link AcessoService}) nao recebe token
 * no login: so depois de trocar pela definitiva em
 * {@link #definirSenhaDefinitiva}. Assim nenhum token emitido vale para uma
 * senha que outra pessoa (quem sabe o CPF) poderia conhecer.
 * </p>
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    /**
     * Hash de uma senha aleatoria, usado quando o login nao existe. Sem isso o
     * caminho "usuario inexistente" responderia em microssegundos e o caminho
     * "senha errada" em centenas de milissegundos, entregando pelo tempo de
     * resposta quais logins estao cadastrados.
     */
    private final String hashSentinela;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.hashSentinela = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Usuarios sem escola (hoje, so {@code SUPER_ADMIN}) nao carregam a claim
     * {@code escolaId} no token — ver {@link JwtService#generateAccessToken}.
     */
    @Transactional
    public LoginResponse login(String login, String senha) {
        Usuario usuario = autenticar(login, senha);

        if (usuario.isSenhaProvisoria()) {
            exigirSenhaProvisoriaValida(usuario);
            return LoginResponse.trocaDeSenhaPendente(usuario.getId(), usuario.getPerfil());
        }

        atualizarHashSeNecessario(usuario, senha);
        return emitirTokens(usuario);
    }

    /**
     * Primeiro acesso: troca a senha provisoria pela definitiva e ja devolve
     * os tokens, sem obrigar um segundo login. So vale para quem ainda esta
     * com senha provisoria; para os demais responde como credencial invalida.
     */
    @Transactional
    public LoginResponse definirSenhaDefinitiva(String login, String senhaProvisoria, String novaSenha) {
        Usuario usuario = autenticar(login, senhaProvisoria);
        if (!usuario.isSenhaProvisoria()) {
            throw new CredenciaisInvalidasException();
        }
        exigirSenhaProvisoriaValida(usuario);

        UsuarioService.validarSenha(novaSenha);
        if (passwordEncoder.matches(novaSenha, usuario.getSenhaHash())) {
            throw new SenhaInvalidaException("Nova senha deve ser diferente da senha provisoria");
        }

        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setSenhaProvisoria(false);
        usuario.setSenhaProvisoriaExpiraEm(null);
        usuarioRepository.save(usuario);
        return emitirTokens(usuario);
    }

    /** Login inexistente, senha errada e usuario inativo sao indistinguiveis. */
    private Usuario autenticar(String login, String senha) {
        Usuario usuario = usuarioRepository
                .findByLogin(UsuarioService.normalizarLogin(login))
                .orElse(null);

        String hash = usuario != null ? usuario.getSenhaHash() : hashSentinela;
        boolean senhaConfere = passwordEncoder.matches(senha, hash);

        if (usuario == null || !senhaConfere || !usuario.isAtivo()) {
            throw new CredenciaisInvalidasException();
        }
        return usuario;
    }

    private void exigirSenhaProvisoriaValida(Usuario usuario) {
        if (usuario.senhaProvisoriaExpirada(clock.instant())) {
            throw new SenhaProvisoriaExpiradaException();
        }
    }

    private LoginResponse emitirTokens(Usuario usuario) {
        return LoginResponse.autenticado(
                jwtService.generateAccessToken(usuario.getId(), usuario.getPerfil().name(), usuario.getEscolaId()),
                jwtService.generateRefreshToken(usuario.getId()),
                usuario.getId(),
                usuario.getPerfil());
    }

    /**
     * Unico momento em que a senha em texto puro esta disponivel: se o hash foi
     * gerado com algoritmo ou custo antigo, aproveita para regravar no formato
     * atual.
     */
    private void atualizarHashSeNecessario(Usuario usuario, String senha) {
        if (passwordEncoder.upgradeEncoding(usuario.getSenhaHash())) {
            usuario.setSenhaHash(passwordEncoder.encode(senha));
            usuarioRepository.save(usuario);
        }
    }
}
