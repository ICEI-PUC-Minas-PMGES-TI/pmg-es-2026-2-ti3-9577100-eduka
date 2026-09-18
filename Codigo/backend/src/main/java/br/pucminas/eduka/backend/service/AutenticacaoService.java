package br.pucminas.eduka.backend.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.dto.LoginRequest;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.model.Usuario;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * Login (RF03). A senha recebida nunca e comparada com a coluna diretamente:
 * passa sempre por {@link PasswordEncoder#matches}.
 */
@Service
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Hash de uma senha aleatoria, usado quando o login nao existe. Sem isso o
     * caminho "usuario inexistente" responderia em microssegundos e o caminho
     * "senha errada" em centenas de milissegundos, entregando pelo tempo de
     * resposta quais logins estao cadastrados.
     */
    private final String hashSentinela;

    public AutenticacaoService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hashSentinela = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse autenticar(LoginRequest request) {
        Usuario usuario = usuarioRepository
                .findByLogin(UsuarioService.normalizarLogin(request.login()))
                .orElse(null);

        String hash = usuario != null ? usuario.getSenhaHash() : hashSentinela;
        boolean senhaConfere = passwordEncoder.matches(request.senha(), hash);

        if (usuario == null || !senhaConfere || !usuario.isAtivo()) {
            throw new CredenciaisInvalidasException();
        }

        atualizarHashSeNecessario(usuario, request.senha());

        return new LoginResponse(
                jwtService.generateAccessToken(usuario.getId(), usuario.getPerfil().name(),
                        usuario.getEmpresaId()),
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
