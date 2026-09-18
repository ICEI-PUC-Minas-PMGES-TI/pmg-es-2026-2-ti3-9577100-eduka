package br.pucminas.eduka.backend.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.dto.CadastroUsuarioRequest;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.exception.LoginJaCadastradoException;
import br.pucminas.eduka.backend.exception.SenhaInvalidaException;
import br.pucminas.eduka.backend.model.Usuario;
import br.pucminas.eduka.backend.repository.UsuarioRepository;

/**
 * Cadastro e troca de senha. A senha em texto puro so existe dentro destes
 * metodos: o que sai daqui rumo ao banco e sempre o hash.
 */
@Service
public class UsuarioService {

    static final int SENHA_TAMANHO_MINIMO = 8;

    /**
     * O BCrypt ignora tudo depois do 72o byte. Aceitar senha maior daria a falsa
     * impressao de forca extra e faria duas senhas diferentes colidirem.
     */
    static final int SENHA_TAMANHO_MAXIMO_BYTES = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario cadastrar(CadastroUsuarioRequest request) {
        String login = normalizarLogin(request.login());
        validarSenha(request.senha());

        if (usuarioRepository.existsByLogin(login)) {
            throw new LoginJaCadastradoException(login);
        }

        Usuario usuario = Usuario.builder()
                .escolaId(request.escolaId())
                .pessoaId(request.pessoaId())
                .login(login)
                .senhaHash(passwordEncoder.encode(request.senha()))
                .perfil(request.perfil())
                .ativo(true)
                .build();

        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void alterarSenha(UUID usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        validarSenha(novaSenha);
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
    }

    /**
     * Logins sao comparados sem diferenciar maiusculas, para que o usuario nao
     * seja barrado por causa do teclado.
     */
    public static String normalizarLogin(String login) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Login nao pode ser vazio");
        }
        return login.trim().toLowerCase(Locale.ROOT);
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_TAMANHO_MINIMO) {
            throw new SenhaInvalidaException(
                    "Senha deve ter ao menos %d caracteres".formatted(SENHA_TAMANHO_MINIMO));
        }
        int bytes = senha.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > SENHA_TAMANHO_MAXIMO_BYTES) {
            throw new SenhaInvalidaException(
                    "Senha nao pode passar de %d bytes".formatted(SENHA_TAMANHO_MAXIMO_BYTES));
        }
    }
}
