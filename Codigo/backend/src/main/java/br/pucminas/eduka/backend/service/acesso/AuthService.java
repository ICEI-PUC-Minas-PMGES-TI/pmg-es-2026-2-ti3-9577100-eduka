package br.pucminas.eduka.backend.service.acesso;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.dto.acesso.LoginResponse;
import br.pucminas.eduka.backend.dto.acesso.VerificacaoLoginResponse;
import br.pucminas.eduka.backend.exception.acesso.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.exception.acesso.SenhaInvalidaException;
import br.pucminas.eduka.backend.exception.acesso.SenhaProvisoriaExpiradaException;
import br.pucminas.eduka.backend.exception.acesso.VerificacaoLoginEncerradaException;
import br.pucminas.eduka.backend.exception.escola.EscolaSuspensaException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
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
 *
 * <p>
 * Com a verificacao em duas etapas ligada, a senha certa tambem nao basta:
 * o login responde com uma verificacao pendente e os tokens so saem em
 * {@link #confirmarCodigo} (ver {@link VerificacaoLoginService}).
 * </p>
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificacaoLoginService verificacaoLoginService;
    private final EscolaRepository escolaRepository;
    private final Clock clock;

    /**
     * Hash de uma senha aleatoria, usado quando o login nao existe. Sem isso o
     * caminho "usuario inexistente" responderia em microssegundos e o caminho
     * "senha errada" em centenas de milissegundos, entregando pelo tempo de
     * resposta quais logins estao cadastrados.
     */
    private final String hashSentinela;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, VerificacaoLoginService verificacaoLoginService,
            EscolaRepository escolaRepository, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificacaoLoginService = verificacaoLoginService;
        this.escolaRepository = escolaRepository;
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
        return concluir(usuario);
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
        return concluir(usuario);
    }

    /**
     * Segunda etapa do login: troca o codigo recebido por e-mail pelos tokens.
     * Sem {@code @Transactional} de proposito: a tentativa errada precisa
     * ficar gravada mesmo com a excecao (ver
     * {@link VerificacaoLoginService#confirmar}).
     */
    public LoginResponse confirmarCodigo(UUID verificacaoId, String codigo) {
        UUID usuarioId = verificacaoLoginService.confirmar(verificacaoId, codigo);
        // Inativado entre a senha e o codigo: nao entra.
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(Usuario::isAtivo)
                .orElseThrow(VerificacaoLoginEncerradaException::new);
        // Suspensa entre a senha e o codigo: tambem nao entra.
        exigirEscolaAtiva(usuario);
        return emitirTokens(usuario);
    }

    public VerificacaoLoginResponse reenviarCodigo(UUID verificacaoId) {
        return verificacaoLoginService.reenviar(verificacaoId);
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
        exigirEscolaAtiva(usuario);
        return usuario;
    }

    /**
     * Escola suspensa pelo SUPER_ADMIN (RF24): ninguem dela entra. O
     * SUPER_ADMIN nao tem escola e passa direto.
     */
    private void exigirEscolaAtiva(Usuario usuario) {
        if (usuario.getEscolaId() == null) {
            return;
        }
        Escola escola = escolaRepository.findById(usuario.getEscolaId()).orElse(null);
        if (escola == null || !escola.isAtivo()) {
            throw new EscolaSuspensaException(escola == null ? null : escola.getMotivoSuspensao());
        }
    }

    private void exigirSenhaProvisoriaValida(Usuario usuario) {
        if (usuario.senhaProvisoriaExpirada(clock.instant())) {
            throw new SenhaProvisoriaExpiradaException();
        }
    }

    /**
     * Senha conferida: os tokens so saem direto quando nao ha codigo de
     * verificacao a pedir. Com ele, saem em {@link #confirmarCodigo}.
     */
    private LoginResponse concluir(Usuario usuario) {
        return verificacaoLoginService.iniciar(usuario)
                .map(verificacao -> LoginResponse.verificacaoPendente(usuario.getId(), usuario.getPerfil(),
                        verificacao))
                .orElseGet(() -> emitirTokens(usuario));
    }

    /** Tokens emitidos: e o acesso que conta como atividade da escola no Painel de Escolas. */
    private LoginResponse emitirTokens(Usuario usuario) {
        usuarioRepository.registrarAcesso(usuario.getId(), clock.instant());
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
